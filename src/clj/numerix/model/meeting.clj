(ns numerix.model.meeting
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.model.user :as db-user]
            [numerix.model.tag :as tags]
            [numerix.service.html-cleaner :as html-cleaner]
            [numerix.model.project :as prj]
            [numerix.lib.helpers :as h]
            [numerix.model.membership :as member]
            [numerix.model.user :as user]
            [numerix.model.user-db :as user-db]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth]
            [mongoruff.collection :as ruff]
            [taoensso.encore :as enc]
            [monger.query :as mq]
            [numerix.model.chat-db :as chat-db]))


(def meeting-coll (:meeting db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db meeting-coll)
      (mc/create db meeting-coll {})
      (mc/ensure-index db meeting-coll (array-map :author-id 1) { :unique false })
      (mc/ensure-index db meeting-coll (array-map :project-id 1) { :unique false }))))

(defn before-save [author-id project-id record]
  (->> record
       (tags/lower-case-tags)
       (tags/check-new-tags author-id project-id)))

(def meeting-schema
  {
   :db              (fn [] (db/get-db))
   :collection      meeting-coll
   :update-keys     h/list-keys-without-augments
   :after-read-list nil ;; executed on the whole list of results, can change the resulting element list
   :after-read      nil ;[(fn [rec] (chat-db/attach-chat-room :meetings (:_id rec) rec))]
   :before-save     (fn [rec] (before-save (:author-id rec) (:project-id rec) rec))
   :after-save      nil
                    ;(fn [rec]
                    ;  (when-not (:__chat-room-id rec)
                    ;    (chat-db/attach-chat-room :meetings (:_id rec) rec)
                    ;    )) ;; pass in saved record, can retrieve a new rec from db and return that one with new augments
   }
  )

;; Low level, DB access functions

(defn get-raw-meeting [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db meeting-coll id)))

(defn list-meetings [user]
  (ruff/query-many
    meeting-schema
    (fn [db coll]
      (let [prj-ids (project-db/visible-project-ids user)
            sorted (mq/with-collection db coll
                                       (mq/find {:project-id {$in prj-ids}})
                                       ;; it is VERY IMPORTANT to use array maps with sort
                                       (mq/sort (array-map :date -1)))]
        (into [] sorted)))))

;(defn list-meetings [user]
;  (let [db (db/get-db)
;        prj-ids (project-db/visible-project-ids user)
;
;        sorted (mq/with-collection db meeting-coll
;                                   (mq/find {:project-id {$in prj-ids}})
;                                   ;; it is VERY IMPORTANT to use array maps with sort
;                                   (mq/sort (array-map :date -1)))
;        ;result (mc/find-maps db meeting-coll {:project-id {$in prj-ids}})
;        ;sorted (reverse (sort-by #(:date %) result))
;        ]
;    (into [] sorted)))

(defn update-meeting [user-id meeting]
  (ruff/update meeting-schema meeting))

;(defn update-meeting [user-id meeting]
;  (let [db (db/get-db)]
;    (if (:_id meeting)
;      (do
;        (mc/update-by-id db meeting-coll (:_id meeting) (before-save user-id (:project-id meeting) meeting))
;        meeting)
;      (mc/insert-and-return db meeting-coll (before-save user-id (:project-id meeting) meeting)))))


(defn remove-meeting [meeting]
  (let [db (db/get-db)]
    ;(log/info "entry: " (pr-str meeting))
    (mc/remove-by-id db meeting-coll (:_id meeting))))


;;; WS Communication functions

(defmethod event-msg-handler :meeting/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :meetings membership)
     meetings (list-meetings user)]

    [:meeting/list { :code :ok, :data { :result meetings }}]
    [:meeting/list { :code :error :msg "Error listing meetings"} ]))

(defmethod event-msg-handler :meeting/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "save meeting " (pr-str ?data))
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     meeting ?data
     has-access? (form-auth/as-form-edit? :meetings membership user-id meeting (get-raw-meeting (:_id meeting)))
     cleaned-entry (update meeting :text html-cleaner/clean-html)
     upd-meeting (update-meeting user-id cleaned-entry)]

    [:meeting/save { :code :ok, :data { :result upd-meeting }}]
    [:meeting/save { :code :error :msg "Error saving meeting"} ]))

(defmethod event-msg-handler :meeting/switch-project
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user    (user-db/get-user user-id)
     old-membership (member/get-membership-by-user user)
     new-membership (member/get-membership (get-in ?data [:project :_id]) user-id)
     meeting (:meeting ?data)
     has-access? (form-auth/as-form-switch-prj?
                   :meetings
                   old-membership
                   new-membership
                   user-id
                   meeting
                   (get-raw-meeting (:_id meeting)))

     upd-meeting (update-meeting
                           user-id
                           (assoc meeting :project-id (:project-id new-membership)))]

    [:meeting/switch-project {:code :ok :data {
                                               :result  upd-meeting
                                               :is-visible (project-db/visible-project-id? user (:project-id new-membership))}}]
    [:meeting/switch-project { :code :error }]))

(defmethod event-msg-handler :meeting/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     meeting (get-raw-meeting ?data)
     has-access? (form-auth/as-form-remove? :meetings membership user-id meeting)
     upd-meeting (remove-meeting meeting)]

    [:meeting/remove { :code :ok }]
    [:meeting/remove { :code :error }]))
