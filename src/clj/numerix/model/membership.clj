(ns numerix.model.membership
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.query :as mq]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [taoensso.encore :as enc]
            [agynamix.roles :as roles]
            [numerix.model.user-db :as user-db]
            [clojure.string :as str]
            [numerix.validation.auth-vali :as auth-vali]
            [validateur.validation :as v]
            [numerix.config :refer [C]]
            [numerix.lib.helpers :as h]
            [numerix.components.mailer :as mailer]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth]
            [numerix.model.presence :as presence]
            [mongoruff.collection :as ruff]))

(def membership-coll (:membership db/coll))

;; NOTE
;; We currently do not use projects as a container structure. Maybe later.

;{
; :_id ObjectId
; :author-id ObjectId
; :project-id ObjectId
; :roles
; :permissions
; :connection-status (:connected|:invited)
;}

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'project-members'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db membership-coll)
      (mc/create db membership-coll {})
      (mc/ensure-index db membership-coll (array-map :author-id 1) { :unique false})
      (mc/ensure-index db membership-coll (array-map :email 1) { :unique false})
      (mc/ensure-index db membership-coll (array-map :connection-status 1) { :unique false})
      (mc/ensure-index db membership-coll (array-map :token 1) { :unique false})
      (mc/ensure-index db membership-coll (array-map :expires 1) { :unique false})
      )))

(def member-schema
  {
   :db (fn[] (db/get-db))
   :collection membership-coll
   :update-keys h/list-keys-without-augments
   :after-read-list nil ;; executed on the whole list of results, can change the resulting element list
   :after-read nil
   :before-save nil
   :after-save nil
   }
  )

(defn get-membership-by-id [membership-id]

  (let [db (db/get-db)]
    (mc/find-one-as-map db membership-coll
                        {:_id membership-id})))

(defn get-invited-member-by-token [token]

  (let [db (db/get-db)]
    (mc/find-one-as-map db membership-coll
                        {:token token
                         :connection-status :invited})))

(defn get-membership [project-id user-id]

  (let [db (db/get-db)]
    (mc/find-one-as-map db membership-coll
                        {:project-id project-id
                         :user-id user-id})))

(defn get-membership-by-user-id [user-id]
  (when-let [partial-user (user-db/get-user-project-id user-id)]
    (let [re (get-membership (:current-project partial-user) user-id)]
      re)))


(defn get-membership-by-user [user]
  (get-membership (:current-project user) (:_id user)))

(defn get-membership-by-email [project-id email]

  (let [db (db/get-db)]
    (mc/find-one-as-map db membership-coll
                        {:project-id project-id
                         :email email})))

(defn list-active-projects-for-user [user-id]
  (let [db (db/get-db)]
    (mc/find-maps db membership-coll {:user-id user-id
                                      :connection-status :active})))

(defn list-invited-project-ids-for-user [user-id]
  (let [db (db/get-db)]
    (mc/find-maps db membership-coll {:user-id user-id
                                      :connection-status :invited} [:project-id])))

(defn create-membership
  "Create a membership connetion between a project and a user and give that membership the proper roles"
  [membership]

  (let [db (db/get-db)
        _   (log/infof "Project Membership: %s" (pr-str membership))
        rec (mc/insert-and-return db membership-coll membership)]
    rec))

(defn count-memberships-for-project [project-id]
  (let [db (db/get-db)]
    (mc/count db membership-coll { :project-id project-id })))

;(def member-schema
;  {
;   :db (fn[] (db/get-db))
;   :collection membership-coll
;   :update-keys h/list-keys-without-augments
;   :after-read-list [(fn [lst])] ;; executed on the whole list of results, can change the resulting element list
;   :after-read nil
;   :before-save nil
;   :after-save nil ;; pass in saved record, can retrieve a new rec from db and return that one with new augments
;   }
;  )

(defn add-user-name-and-presence [memberships]
  (let [users (user-db/find-users-by-id (map :user-id memberships))
        connected-uids (:any @(:connected-uids (C :ws-connection)))
        connected-emails (into #{} (map config/get-identity-from-presence connected-uids))]
    (mapv (fn [m]
            (if-let [user (first (filter #(= (:_id %) (:user-id m)) users))]
              (assoc m :__user-name (h/name-or-email user)
                       :__user-profile-img-id (:profile-image-id user)
                       :__presences (presence/list-connected-by-user-id (:_id user) connected-uids)
                       :__online-status (if (and
                                            (contains? connected-emails (:email user))
                                            (= (:current-project user) (:project-id m)))
                                        :online
                                        :offline))

              m)) memberships)))

(defn list-memberships-for-project
  "Lists all memberships for the given project id.
  Does not perform authentication, use 'list-memberships-for-project-auth'
  for additional authentication."
  [project-id]

  (let [db (db/get-db)]

    (into [] (mq/with-collection db membership-coll
                                 (mq/find { :project-id project-id })
                                 ;; it is VERY IMPORTANT to use array maps with sort
                                 (mq/sort (array-map :_id -1))))))

(defn list-active-memberships-for-project
  "Lists all active memberships for the given project id."
  [project-id]

  (let [db (db/get-db)]
    (into [] (mq/with-collection db membership-coll
                                 (mq/find {:project-id project-id
                                           :connection-status :active})))))

(defn list-memberships-for-user
  "Lists all memberships for the given user id."
  [user-id]
  (let [db (db/get-db)]
    (into [] (mq/with-collection db membership-coll
                                 (mq/find { :user-id user-id })))))


(defn list-memberships-for-project-auth
  "Lists all memberships of this users project if the user has project-admin:edit permission."
  [user-with-prj]
  (enc/if-let [membership (get-membership (:current-project user-with-prj) (:_id user-with-prj))
               _ (form-auth/as-form-read? :memberships membership)
               memberships (list-memberships-for-project (:current-project user-with-prj))
               memberships (add-user-name-and-presence memberships)]

              memberships
              []))

(defn list-memberships-for-project-reduced
  "Lists all memberships of this users project if the user has project-admin:edit permission."
  [user-with-prj]
  (let [memberships (list-memberships-for-project (:current-project user-with-prj))
        reduced (mapv (fn [it]
                        (select-keys it [:_id :project-id :user-id :email])) memberships)
        reduced (add-user-name-and-presence reduced)]

    reduced))

(defn invite-new-members [inviter invitation]
  (enc/when-let
    [db (db/get-db)
     project (project-db/get-project (:project-id invitation))
     emails (map str/trim (str/split (:emails invitation) #"\s+"))]

    (doall
      (into []
            (reduce
              (fn [akku email]
                (enc/if-let
                  [valid? (auth-vali/is-email? email)
                   new-to-prj? (nil? (get-membership-by-email (:project-id invitation) email))]
                  (conj akku
                        (-> invitation
                            (dissoc :emails)
                            (assoc :email (str/lower-case email)
                                   :connection-status :invited
                                   :expires (h/calc-expires-date h/invite-expires-days)
                                   :token (user-db/create-login-token))
                            ((fn [invitation]
                               (if-let [exstng-id (user-db/get-userid-by-email email)]
                                 (assoc invitation :user-id exstng-id)
                                 invitation)))
                            (create-membership)
                            ((fn [invitation]
                               (mailer/send-project-invitation (C :mailer) project inviter invitation)
                               invitation
                               ))
                            ))

                  akku)) nil emails)))))

;(defn update-membership [membership]
;  (let [db (db/get-db)]
;    ;(log/info "membership: " (pr-str membership))
;    (if (:_id membership)
;      (do
;        (mc/update-by-id db membership-coll (:_id membership) membership)
;        membership)
;      (mc/insert-and-return db membership-coll membership))))

(defn update-membership [membership]
  (ruff/update member-schema membership))


(defn remove-membership [membership]
  (let [db (db/get-db)]
    ;(log/info "membership: " (pr-str membership))
    (mc/remove-by-id db membership-coll (:_id membership))))


(defmethod event-msg-handler :membership/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     my-membership (get-membership (:current-project user) (:_id user))
     membership ?data
     has-access? (form-auth/as-form-edit-membership? my-membership user-id membership (get-membership-by-id (:_id ?data)))
     upd-membership (update-membership membership)]

    [:membership/save { :code :ok, :data { :result upd-membership }}]
    [:membership/save { :code :error}]))

(defmethod event-msg-handler :membership/invite
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     my-membership (get-membership (:current-project user) (:_id user))
     project-member-count (count-memberships-for-project (:current-project user))
     invitation-valid? (v/valid? (auth-vali/invite-members-validations ?data))
     total-count (<= (+ (auth-vali/count-tokens (:emails ?data) auth-vali/invitation-emails-split-regex) project-member-count)
                    auth-vali/max-members-per-project)
     invitation ?data
     has-access? (form-auth/as-form-create-membership? my-membership user-id invitation)
     memberships (invite-new-members user invitation)]

    [:membership/invite { :code :ok :data { :result memberships } :msg "Invitation messages send successfully" }]
    [:membership/invite { :code :error :msg "The given list of email addresses was not valid."}]))

(defmethod event-msg-handler :membership/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "remove, got: " (pr-str ?data))
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     my-membership (get-membership (:current-project user) (:_id user))

     membership (get-membership-by-id ?data)
     has-access? (form-auth/as-form-remove-membership? my-membership user-id membership)
     upd-membership (remove-membership membership)]

    [:membership/remove { :code :ok }]
    [:membership/remove { :code :error }]))
