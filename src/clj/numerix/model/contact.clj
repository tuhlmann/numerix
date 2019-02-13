(ns numerix.model.contact
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.model.project :as prj]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.model.user :as user]
            [numerix.model.membership :as member]
            [numerix.model.user-db :as user-db]
            [taoensso.encore :as enc]
            [agynamix.roles :as roles]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth]))

(def contacts-coll (:contact db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'companies'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db contacts-coll)
      (mc/create db contacts-coll {})
      (mc/ensure-index db contacts-coll (array-map :author-id 1) { :unique false})
      (mc/ensure-index db contacts-coll (array-map :project-id 1) { :unique false }))))

;; Low level, DB access functions

(defn get-contact [id]
  (enc/when-let [_ id
                 db (db/get-db)]
    (mc/find-map-by-id db contacts-coll id)))

(defn list-contacts-for-user [user]
  (enc/if-let
    [db (db/get-db)
     prj-ids (project-db/visible-project-ids user)
     result (mc/find-maps db contacts-coll {:project-id {$in prj-ids}})
     sorted (sort-by #(:name %) result)]
              (into [] sorted)))

(defn update-contact [contact]
  (let [db (db/get-db)]
    ;(log/info "Contact: " (pr-str contact))
    (if (:_id contact)
      (do
        (mc/update-by-id db contacts-coll (:_id contact) contact)
        contact)
      (mc/insert-and-return db contacts-coll contact))))


(defn remove-contact [contact]
  (let [db (db/get-db)]
    ;(log/info "Contact: " (pr-str contact))
    (mc/remove-by-id db contacts-coll (:_id contact))))


;;; WS Communication functions

(defmethod event-msg-handler :contact/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :contacts membership)
     contacts (list-contacts-for-user user)]

    [:contact/list { :code :ok, :data { :result contacts }}]
    [:contact/list { :code :error :msg "Error listing contacts"} ]))

(defmethod event-msg-handler :contact/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     contact ?data
     has-access? (form-auth/as-form-edit? :contacts membership user-id contact (get-contact (:_id ?data)))
     upd-contact (update-contact contact)]

    [:contact/save { :code :ok, :data { :result upd-contact}}]
    [:contact/save { :code :error}]))

(defmethod event-msg-handler :contact/switch-project
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user    (user-db/get-user user-id)
     old-membership (member/get-membership-by-user user)
     new-membership (member/get-membership (get-in ?data [:project :_id]) user-id)
     contact (:contact ?data)
     has-access? (form-auth/as-form-switch-prj?
                   :contacts
                   old-membership
                   new-membership
                   user-id
                   contact
                   (get-contact (:_id contact)))

     upd-contact (update-contact (assoc contact :project-id (:project-id new-membership)))]

    [:contact/switch-project { :code :ok
                              :data { :result upd-contact
                                      :is-visible (project-db/visible-project-id? user (:project-id new-membership))}}]
    [:contact/switch-project { :code :error }]))



(defmethod event-msg-handler :contact/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     contact (get-contact ?data)
     has-access? (form-auth/as-form-remove? :contacts membership user-id contact)
     upd-contact (remove-contact contact)]

    [:contact/remove { :code :ok}]
    [:contact/remove { :code :error}]))

