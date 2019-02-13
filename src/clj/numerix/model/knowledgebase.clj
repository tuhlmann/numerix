(ns numerix.model.knowledgebase
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
            [taoensso.encore :as enc]))

(def kn-base-coll (:knowledgebase db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db kn-base-coll)
      (mc/create db kn-base-coll {})
      (mc/ensure-index db kn-base-coll (array-map :author-id 1) { :unique false })
      (mc/ensure-index db kn-base-coll (array-map :project-id 1) { :unique false }))))

(defn before-save [author-id project-id record]
  (->> record
      (tags/lower-case-tags)
      (tags/check-new-tags author-id project-id)))

;; Low level, DB access functions

(defn get-knowledge-entry [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db kn-base-coll id)))

(defn list-knowledge-entries [user]
  (let [db (db/get-db)
        prj-ids (project-db/visible-project-ids user)
        result (mc/find-maps db kn-base-coll {:project-id {$in prj-ids}})
        sorted (reverse (sort-by #(:date %) result))]
    (into [] sorted)))

(defn update-knowledge-entry [user-id knowledge-entry]
  (let [db (db/get-db)]
    (if (:_id knowledge-entry)
      (do
        (mc/update-by-id db kn-base-coll (:_id knowledge-entry) (before-save user-id (:project-id knowledge-entry) knowledge-entry))
        knowledge-entry)
      (mc/insert-and-return db kn-base-coll (before-save user-id (:project-id knowledge-entry) knowledge-entry)))))


(defn remove-knowledge-entry [knowledge-entry]
  (let [db (db/get-db)]
    ;(log/info "entry: " (pr-str knowledge-entry))
    (mc/remove-by-id db kn-base-coll (:_id knowledge-entry))))


;;; WS Communication functions

(defmethod event-msg-handler :knowledgebase/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :knowledgebase membership)
     knowledge-entries (list-knowledge-entries user)]

    [:knowledgebase/list { :code :ok, :data { :result knowledge-entries }}]
    [:knowledgebase/list { :code :error :msg "Error listing knowledge entries"} ]))

(defmethod event-msg-handler :knowledgebase/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     knowledge-entry ?data
     has-access? (form-auth/as-form-edit? :knowledgebase membership user-id knowledge-entry (get-knowledge-entry (:_id ?data)))
     cleaned-entry (update knowledge-entry :text html-cleaner/clean-html)
     upd-knowledge-entry (update-knowledge-entry user-id cleaned-entry)]

    [:knowledgebase/save { :code :ok, :data { :result upd-knowledge-entry }}]
    [:knowledgebase/save { :code :error :msg "Error saving knowledge entry"} ]))

(defmethod event-msg-handler :knowledgebase/switch-project
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user    (user-db/get-user user-id)
     old-membership (member/get-membership-by-user user)
     new-membership (member/get-membership (get-in ?data [:project :_id]) user-id)
     knowledge-entry (:knowledgebase ?data)
     has-access? (form-auth/as-form-switch-prj?
                   :knowledgebase
                   old-membership
                   new-membership
                   user-id
                   knowledge-entry
                   (get-knowledge-entry (:_id knowledge-entry)))

     upd-knowledge-entry (update-knowledge-entry
                           user-id
                           (assoc knowledge-entry :project-id (:project-id new-membership)))]

    [:knowledgebase/switch-project {:code :ok :data {
                                                 :result  upd-knowledge-entry
                                                 :is-visible (project-db/visible-project-id? user (:project-id new-membership))}}]
    [:knowledgebase/switch-project { :code :error }]))

(defmethod event-msg-handler :knowledgebase/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     knowledge-entry (get-knowledge-entry ?data)
     has-access? (form-auth/as-form-remove? :knowledgebase membership user-id knowledge-entry)
     upd-knowledge-entry (remove-knowledge-entry knowledge-entry)]

    [:knowledgebase/remove { :code :ok }]
    [:knowledgebase/remove { :code :error }]))
