(ns numerix.model.project-db
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.lib.roles          :as roles]
            [taoensso.encore :as enc]))

(def project-coll (:project db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'projects'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db project-coll)
      (mc/create db project-coll {})
      (mc/ensure-index db project-coll (array-map :author-id 1) { :unique false}))))


;; Low level, DB access functions

(defn get-project
  "Get Project by its ID"
  [id]
  (let [db (db/get-db)]
    (mc/find-map-by-id db project-coll id)))

(defn list-project-ids-for-user [user]
  "Finds the project ids for this user."
  ;(let [db (db/get-db)
  ;      memberships (mc/find-maps db project-member/project-member-coll {:user-id (:_id user)} [:project-id])]
  ;  (mapv :project-id memberships))

  [(:current-project user)])

(defn remove-project [project]
  (let [db (db/get-db)]
    (log/infof "Project: %s" (pr-str project))
    (mc/remove-by-id db project-coll (:_id project))))

;;; API functions for others

(defn visible-project-ids [user]
  "Currently only the current project is visible.
  When we implement sharing, data from projects that share becomes visible as well"
  (list-project-ids-for-user user))

(defn visible-project-id? [user project-id]
  (contains? (into #{} (visible-project-ids user)) project-id))

