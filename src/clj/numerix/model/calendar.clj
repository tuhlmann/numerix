(ns numerix.model.calendar
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
            [monger.query :as mq])
  (:import (org.bson.types ObjectId)))


(def cal-item-coll (:cal-item db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db cal-item-coll)
      (mc/create db cal-item-coll {})
      (mc/ensure-index db cal-item-coll (array-map :author-id 1) { :unique false })
      (mc/ensure-index db cal-item-coll (array-map :project-id 1) { :unique false }))))

(def cal-item-schema
  {
   :db              (fn [] (db/get-db))
   :collection      cal-item-coll
   :update-keys     h/list-keys-without-augments
   :after-read-list nil ;; executed on the whole list of results, can change the resulting element list
   :after-read      nil ;[(fn [rec] (chat-db/attach-chat-room :meetings (:_id rec) rec))]
   :before-save     nil ;(fn [rec] (before-save (:author-id rec) (:project-id rec) rec))
   :after-save      nil
   ;(fn [rec]
   ;  (when-not (:__chat-room-id rec)
   ;    (chat-db/attach-chat-room :meetings (:_id rec) rec)
   ;    )) ;; pass in saved record, can retrieve a new rec from db and return that one with new augments
   }
  )

;; Low level, DB access functions

(defn get-cal-item [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db cal-item-coll id)))

;; fixme limit days to fetch
(defn list-cal-items [user days]
  (ruff/query-many
    cal-item-schema
    (fn [db coll]
      (let [prj-ids (project-db/visible-project-ids user)
            result (mq/with-collection db coll
                                       (mq/find {:project-id {$in prj-ids}}))]
        (into [] result)))))


(defn list-cal-items-range [user start end]
  (ruff/query-many
    cal-item-schema
    (fn [db coll]
      (let [prj-ids (project-db/visible-project-ids user)
            result (mq/with-collection db coll
                                       (mq/find {:project-id {$in prj-ids}
                                                 :start {$gte start
                                                         $lte end}
                                                 }))]
        (into [] result)))))


(defn update-cal-item [cal-item]
  (ruff/update cal-item-schema cal-item))


(defn remove-cal-item [cal-item]
  (let [db (db/get-db)]
    (log/info "remove entry: " (pr-str cal-item))
    (mc/remove-by-id db cal-item-coll (:_id cal-item))))


;;; WS Communication functions

(defmethod event-msg-handler :cal-item/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     days-to-fetch ?data
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :cal-item membership)
     cal-items (list-cal-items user days-to-fetch)]

    [:cal-item/list { :code :ok, :data { :result cal-items }}]
    [:cal-item/list { :code :error :msg "Error listing calendar items"} ]))

(defmethod event-msg-handler :cal-item/get-item-start-date
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     item-id (ObjectId. ?data)
     membership (member/get-membership-by-user user)
     cal-item (get-cal-item item-id)
     has-access? (form-auth/as-form-read-item? :cal-item membership user-id cal-item)]

    [:cal-item/list { :code :ok, :data { :result (:start cal-item) }}]
    [:cal-item/list { :code :error :msg "Error listing calendar item start date"} ]))

(defmethod event-msg-handler :cal-item/list-range
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     range ?data
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :cal-item membership)
     cal-items (list-cal-items-range user (:start range) (:end range))]

    [:cal-item/list { :code :ok, :data { :result cal-items }}]
    [:cal-item/list { :code :error :msg "Error listing calendar items"} ]))

(defmethod event-msg-handler :cal-item/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "save calendar item " (pr-str ?data))
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     cal-item ?data
     has-access? (form-auth/as-form-edit? :cal-item membership user-id cal-item (get-cal-item (:_id cal-item)))
     upd-cal-item (update-cal-item cal-item)]

    [:cal-item/save { :code :ok, :data { :result upd-cal-item }}]
    [:cal-item/save { :code :error :msg "Error saving calendar item"} ]))

;(defmethod event-msg-handler :meeting/switch-project
;  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
;  (auth-let
;    ?reply-fn
;    [user-id (config/get-auth-user-id-from-req ring-req)
;     user    (user-db/get-user user-id)
;     old-membership (member/get-membership-by-user user)
;     new-membership (member/get-membership (get-in ?data [:project :_id]) user-id)
;     meeting (:meeting ?data)
;     has-access? (form-auth/as-form-switch-prj?
;                   :meetings
;                   old-membership
;                   new-membership
;                   user-id
;                   meeting
;                   (get-raw-meeting (:_id meeting)))
;
;     upd-meeting (update-meeting
;                   user-id
;                   (assoc meeting :project-id (:project-id new-membership)))]
;
;    [:meeting/switch-project {:code :ok :data {
;                                               :result  upd-meeting
;                                               :is-visible (project-db/visible-project-id? user (:project-id new-membership))}}]
;    [:meeting/switch-project { :code :error }]))

(defmethod event-msg-handler :cal-item/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "remove, got: " (pr-str ?data))
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     cal-item (get-cal-item ?data)
     has-access? (form-auth/as-form-remove? :cal-item membership user-id cal-item)
     upd-cal-item (remove-cal-item cal-item)]

    [:cal-item/remove { :code :ok }]
    [:cal-item/remove { :code :error }]))

