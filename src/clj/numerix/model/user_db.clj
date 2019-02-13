(ns numerix.model.user-db
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [clojure.pprint :as pprint]
            [numerix.config :refer [C]]
            [buddy.hashers :as hashers]
            [validateur.validation :as v]
            [cuerdas.core :as cue]
            [crypto.random :as random]
            [clj-time.core :as t]
            [clojure.string :as str]
            [numerix.lib.roles :as roles])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]))

(def user-coll (:user db/coll))
(def view-settings-coll (:view-settings db/coll))

(def secured-keys [:_id :password :email])
(def updatable-keys [:name :company :street :zip :city :country :bio :invoice-no-pattern :vat-number :profile-image-id])

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'users'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db user-coll)
      (mc/create db user-coll {})
      (mc/ensure-index db user-coll (array-map :email 1) { :unique true})
      (mc/ensure-index db user-coll (array-map :login-token 1) { :unique false}))
    (when-not (mc/exists? db view-settings-coll)
      (mc/create db view-settings-coll {})
      (mc/ensure-index db view-settings-coll (array-map :author-id 1) { :unique true}))))

(defn add-user [user]
  (let [db (db/get-db)
        clean-user (dissoc user :identity)]
    (mc/insert-and-return db user-coll clean-user)))

(defn get-user [id]
  (let [db (db/get-db)]
    (mc/find-map-by-id db user-coll id)))

(defn get-user-by-email [email]
  (let [db (db/get-db)]
    (mc/find-one-as-map db user-coll {:email (str/lower-case email)})))

(defn get-userid-by-email [email]
  (let [db (db/get-db)]
    (mc/find-one-as-map db user-coll {:email (str/lower-case email)} [:_id])))

(defn get-user-by-req [req]
  (when-let [user-id (config/get-auth-user-id-from-req req)]
    (get-user user-id)))

(defn get-user-by-token [token]
  (let [db (db/get-db)]
    (mc/find-one-as-map db user-coll {:login-token token})))

(defn get-user-project-id [user-id]
  (let [db (db/get-db)]
    (mc/find-map-by-id db user-coll user-id [:current-project])))


(defn update-user [email user]
  (let [db (db/get-db)]
    (mc/update db user-coll {:email email}
               {"$set" (select-keys user updatable-keys)}
               {:multi false})))

(defn update-user-by-id [id user]
  (let [db (db/get-db)
        oid (if (string? id) (ObjectId. id) id)]
    (mc/update db user-coll {:_id oid}
               {"$set" (select-keys user updatable-keys)}
               {:multi false})))

(defn update-user-keys [user & {:keys [set unset]}]
  (let [db (db/get-db)
        work {"$set" (select-keys user set)
              "$unset" (into {} (map (fn [v] [v ""]) unset))}
        work (if-not unset (dissoc work "$unset") work)
        work (if-not set (dissoc work "$set") work)]
    (mc/update db user-coll {:email (:email user)} work {:multi false})
    (get-user (:_id user))))

(defn change-user-password [user-id new-pwd]
  (let [db (db/get-db)]
    (mc/update db user-coll {:_id user-id}
               {"$set" {:password (hashers/encrypt new-pwd)}}
               {:multi false})))

(defn create-login-token []
  (cue/slugify (random/base64 40)))

(defn set-login-token [user token expires]
  (let [db (db/get-db)]
    (mc/update-by-id db user-coll (:_id user)
                     {"$set" {:login-token token
                              :login-token-expires expires}}
                     {:multi false})))

(defn set-current-project [user-id project-id]
  (let [db (db/get-db)]
    (mc/update-by-id db user-coll user-id
                     {"$set" {:current-project project-id}}
                     {:multi false})
    (get-user user-id)))

(defn create-new-user
  "Creates a new user and associated structures."
  [{:keys [email password admin] :as user-data}]
  (-> (dissoc user-data :admin)
      (update :email str/lower-case)
      (assoc :identity email
             :password (hashers/encrypt password)
             :roles (into #{roles/role-user-all} (when admin [roles/role-admin-all]))
             :login-token (create-login-token)
             :login-token-expires (t/plus (t/now) (t/hours 24))
             :validated false)))


(defn get-view-settings [user-id]
  (let [db (db/get-db)]
    (mc/find-one-as-map db view-settings-coll {:author-id user-id})))

(defn update-view-settings [user-id settings]
  (let [db (db/get-db)
        settings-with-id (merge settings {:author-id user-id})]
    (log/debug "User Settings " (pr-str settings-with-id))
    (mc/upsert db view-settings-coll {:author-id user-id} settings-with-id)))


(defn find-current-projects-by-emails [emails]
  ;(log/info "find project-ids for emails " emails)
  (let [db (db/get-db)
        users (mc/find-maps db user-coll {:email {$in emails}} [:email :current-project])]
    users))


(defn find-users-by-id [user-ids]
  (let [db (db/get-db)]
    (mc/find-maps db user-coll {:_id {$in user-ids}} [:email
                                                      :current-project
                                                      :name
                                                      :profile-image-id])))



(defn remove-secure-fields [data]
  (-> data
      (dissoc :password)))



