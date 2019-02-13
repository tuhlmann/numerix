(ns numerix.model.timeroll
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
            [numerix.model.project :as prj]
            [numerix.model.membership :as member]
            [numerix.model.user-db :as user-db]
            [numerix.model.user :as user]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth]
            [taoensso.encore :as enc])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]))

(def timeroll-coll (:timeroll db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db timeroll-coll)
      (mc/create db timeroll-coll {})
      (mc/ensure-index db timeroll-coll (array-map :author-id 1) { :unique false })
      (mc/ensure-index db timeroll-coll (array-map :project-id 1) { :unique false }))))

;; Low level, DB access functions

(defn get-timeroll-entry [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db timeroll-coll id)))

(defn list-timeroll-entries-for-user [user]
  (let [db (db/get-db)
        prj-ids (project-db/visible-project-ids user)
        result (mc/find-maps db timeroll-coll {:project-id {$in prj-ids}})

        sorted (reverse (sort-by #(:date %) result))]
    (into [] sorted)))

(defn update-timeroll-entry [timeroll-entry]
  (let [db (db/get-db)]
    (if (:_id timeroll-entry)
      (do
        (mc/update-by-id db timeroll-coll (:_id timeroll-entry) timeroll-entry)
        timeroll-entry)
      (mc/insert-and-return db timeroll-coll timeroll-entry))))


(defn remove-timeroll-entry [timeroll-entry]
  (let [db (db/get-db)]
    (mc/remove-by-id db timeroll-coll (:_id timeroll-entry))))


;;; WS Communication functions

(defmethod event-msg-handler :timeroll/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :timeroll membership)
     timeroll-entries (list-timeroll-entries-for-user user)]

    [:timeroll/list { :code :ok, :data { :result timeroll-entries }}]
    [:timeroll/list { :code :error :msg "Error listing timeroll entries"} ]))


(defmethod event-msg-handler :timeroll/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     timeroll-entry ?data
     has-access? (form-auth/as-form-edit? :timeroll membership user-id timeroll-entry (get-timeroll-entry (:_id ?data)))
     upd-timeroll-entry (update-timeroll-entry timeroll-entry)]

    [:timeroll/save { :code :ok, :data { :result upd-timeroll-entry }}]
    [:timeroll/save { :code :error}]))

(defmethod event-msg-handler :timeroll/switch-project
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user    (user-db/get-user user-id)
     old-membership (member/get-membership-by-user user)
     new-membership (member/get-membership (get-in ?data [:project :_id]) user-id)
     timeroll-entry (:timeroll ?data)
     has-access? (form-auth/as-form-switch-prj?
                   :timeroll
                   old-membership
                   new-membership
                   user-id
                   timeroll-entry
                   (get-timeroll-entry (:_id timeroll-entry)))

     upd-timeroll-entry (update-timeroll-entry (assoc timeroll-entry :project-id (:project-id new-membership)))]

    [:timeroll/switch-project {:code :ok
                               :data {:result   upd-timeroll-entry
                                      :is-visible (project-db/visible-project-id? user (:project-id new-membership))}}]
    [:timeroll/switch-project { :code :error }]))

(defmethod event-msg-handler :timeroll/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     timeroll-entry (get-timeroll-entry ?data)
     has-access? (form-auth/as-form-remove? :timeroll membership user-id timeroll-entry)
     upd-timeroll-entry (remove-timeroll-entry timeroll-entry)]

    [:timeroll/remove { :code :ok }]
    [:timeroll/remove { :code :error }]))
