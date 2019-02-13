(ns numerix.model.tag
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
            [numerix.lib.helpers :as h]
            [numerix.model.project :as prj]
            [clojure.string :as str]
            [clojure.data]
            [numerix.lib.roles :as roles]
            [numerix.model.user-db :as user-db]
            [numerix.model.membership :as member]
            [numerix.auth.form-auth :as form-auth]
            [taoensso.encore :as enc]))

(def tag-coll (:tag db/coll))

;; A tag has a
;; - _id
;; - label (lower case)
;; - user-id


(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'tags'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db tag-coll)
      (mc/create db tag-coll {})
      (mc/ensure-index db tag-coll (array-map :author-id 1) { :unique false } )
      (mc/ensure-index db tag-coll (array-map :project-id 1) { :unique false } )
      (mc/ensure-index db tag-coll (array-map :label 1) { :unique false }))))

;; Low level, DB access functions

(defn get-tag [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db tag-coll id)))

;(defn find-tags [ids]
;  (let [db (db/get-db)
;        result (mc/find-maps db tag-coll {:_id {$in ids}})
;        sorted (sort-by #(.indexOf ids (:_id %)) result)]
;    (log/info "Sorted tags " (doall (map :_id sorted)))
;    (into [] sorted)))

(defn find-project-tags-by-label [project-id labels]
  (let [db (db/get-db)
        result (mc/find-maps db tag-coll {:project-id project-id :label {$in labels}})]
    (into [] result)))

(defn list-tags-for-user [user]
  (let [db (db/get-db)
        result (mc/find-maps db tag-coll {:author-id (:_id user) })
        sorted (sort-by #(:label %) result)]
    (into [] sorted)))

(defn list-tags-for-project [project-id]
  (let [db (db/get-db)
        result (mc/find-maps db tag-coll {:project-id project-id })
        sorted (sort-by #(:label %) result)]
    (into [] sorted)))

(defn update-tag [tag]
  (let [db (db/get-db)]
    (if (:_id tag)
      (do
        (mc/update-by-id db tag-coll (:_id tag) tag)
        tag)
      (mc/insert-and-return db tag-coll tag))))


(defn remove-tag [tag]
  (let [db (db/get-db)]
    (log/info "Tag: " (pr-str tag))
    (mc/remove-by-id db tag-coll (:_id tag))))


;;; WS Communication functions

(defmethod event-msg-handler :tag/list-for-user
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? roles/all-domains membership)
     tags (list-tags-for-user user)]

    [:tag/list-for-user { :code :ok, :data { :result tags }}]
    [:tag/list-for-user { :code :error :msg "Error listing tags"} ]))


(defmethod event-msg-handler :tag/list-for-project
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info ":tag/list-for-project")
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? roles/all-domains membership)
     tags (list-tags-for-project (:current-project user))]

    [:tag/list-for-project { :code :ok, :data { :result tags }}]
    [:tag/list-for-project { :code :error :msg "Error listing tags"} ]))


  ;(if ?reply-fn
  ;  (let [user-id (config/get-auth-user-id-from-req ring-req)
  ;        user (user-db/get-user user-id)
  ;        tags (list-tags-for-project (:current-project user))]
  ;    (log/debug "GOT "(prn-str tags))
  ;    (?reply-fn [:tag/list-for-project { :code :ok, :data { :result tags}}]))))


(defmethod event-msg-handler :project/roles-list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (if ?reply-fn
    (let [user-id (config/get-auth-user-id-from-req ring-req)
          ;user (db-user/get-user user-id)
          roles (roles/list-available-roles-for-select)]
      (log/debug "GOT "(prn-str roles))
      (?reply-fn [:project/roles-list { :code :ok, :data { :result roles}}]))))



(defmethod event-msg-handler :tag/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     tag ?data
     has-access? (form-auth/as-form-edit? roles/all-domains membership user-id tag (get-tag (:_id ?data)))
     upd-tag (update-tag tag)]

    [:tag/save { :code :ok, :data { :result upd-tag }}]
    [:tag/save { :code :error}]))

(defmethod event-msg-handler :tag/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "remove, got: " (pr-str ?data))
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     tag (get-tag ?data)
     has-access? (form-auth/as-form-remove? roles/all-domains membership user-id tag)
     upd-tag (remove-tag tag)]

    [:tag/remove { :code :ok }]
    [:tag/remove { :code :error }]))

;;; Utility functions

(defn lower-case-tags [record]
  (assoc record :tags (mapv #(str/lower-case %) (:tags record))))

(defn check-new-tags [author-id project-id record]
  (let [rec-tags (:tags record)
        found-tags (find-project-tags-by-label project-id rec-tags)
        found-labels (into #{} (map :label found-tags))]

    (doseq [label rec-tags]
      (when (and
              (not (contains? found-labels label))
              (seq label))
        (update-tag {:author-id author-id
                     :project-id project-id
                     :label label})))

    record
    ))

