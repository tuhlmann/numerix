(ns numerix.model.project
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.model.membership :as member]
            [numerix.model.user-db :as user-db]
            [numerix.model.project-db :as project-db]
            [clj-time.core :as t]
            [taoensso.encore :as enc]
            [agynamix.roles :as roles]
            [numerix.lib.roles :as roles-data]
            [numerix.auth.form-auth :as form-auth]
            [clojure.string :as str]))

(def user-coll (:user db/coll))
(def project-coll (:project db/coll))

(defn update-project
  "Update a project. If it does not exist we create a membership connection between the new project record
  and the user that creates it. The user becomes admin of the project."
  [project user]

  (let [db (db/get-db)]
    (log/infof "Update Project: %s" (pr-str project))
    (if (:_id project)
      (do
        (mc/update-by-id db project-coll (:_id project) project)
        project)
      (let [rec (mc/insert-and-return db project-coll project)
            member-rec (member/create-membership {:created    (t/now)
                                                  :author-id  (:_id user)
                                                  :project-id (:_id rec)
                                                  :user-id    (:_id user)
                                                  :email      (str/lower-case (:email user))
                                                              :connection-status :active
                                                              :roles [roles-data/role-project-admin-all roles-data/role-project-all]})]
        rec))))


(defn create-project-for-user
  "Create a new project for the given user."
  [& {:keys [user name]
      :or {name "Default project"}}]
  (let [db (db/get-db)
        project {
                 :author-id (:_id user) ;; owner of the project
                 :name name
                 :summary ""}

        _ (log/infof "Create New Project: %s" (pr-str project))]

    (update-project project user)))


(defn list-projects-for-user [user create-initial-prj?]
  "Finds the projects for this user.
  If `create-initial-prj?` is true will create a project for the user."
  (let [db (db/get-db)
        memberships (member/list-active-projects-for-user (:_id user))
        project-ids (mapv :project-id memberships)
        result (mc/find-maps db project-coll {:_id {$in project-ids}})
        ;grouped (group-by
        ;              (fn [p]
        ;                (if (:is-root-project p)
        ;                  :root
        ;                  :child)) result)
        ;sorted (concat (:root grouped) (sort-by #(:name %) (:child grouped)))
        sorted (sort-by #(:name %) result)]
    (if (and (empty? sorted) create-initial-prj?)
      (vector (create-project-for-user
                :user user
                :name (or (:contact user) (:email user))))

      (into [] sorted))))

;;; API functions for others


;;; WS Communication functions


(defmethod event-msg-handler :project/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     project ?data
     has-access? (if (:_id project)
                   (if-let [membership (member/get-membership (:_id project) user-id)]
                     (form-auth/as-form-edit-project? membership user-id project (project-db/get-project (:_id project)))
                     false)
                   (form-auth/as-form-create-project? user-id project))

     upd-project (update-project project user)]

    [:project/save { :code :ok, :data { :result upd-project} :msg "Project saved."}]
    [:project/save { :code :error :msg "Error saving the project"}]))


(defmethod event-msg-handler :project/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     project (project-db/get-project ?data)
     membership (member/get-membership (:_id project) user-id)
     has-access? (form-auth/as-form-remove-project? membership user-id (:current-project user) project)
     upd-project (project-db/remove-project project)]

    [:project/remove { :code :ok :msg "Project removed"}]
    [:project/remove { :code :error :msg "Error removing the project"}]))

