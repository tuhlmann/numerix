(ns numerix.model.textblock
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
            [numerix.model.membership :as member]
            [numerix.model.user-db :as user-db]
            [numerix.model.user :as user]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth]
            [taoensso.encore :as enc]))

(def textblock-coll (:textblock db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'textblocks'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db textblock-coll)
      (mc/create db textblock-coll {})
      (mc/ensure-index db textblock-coll (array-map :author-id 1) { :unique false } )
      (mc/ensure-index db textblock-coll (array-map :project-id 1) { :unique false }))))

;; Low level, DB access functions

(defn get-textblock [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db textblock-coll id)))

(defn find-textblocks [ids]
  (let [db (db/get-db) ;; http://stackoverflow.com/a/12344565/7143
        result (mc/find-maps db textblock-coll {:_id {$in ids}})
        ;sort-fn (fn [it] (first (h/positions #(= % (:_id it)) ids)))
        ;sorted2 (vec (sort-by sort-fn result))
        sorted (sort-by #(.indexOf ids (:_id %)) result)]
    ;(log/info "Sorted textblocks " (doall (map :_id sorted)))
    (into [] sorted)))

(defn list-textblocks-for-user [user]
  (let [db (db/get-db)
        prj-ids (project-db/visible-project-ids user)
        result (mc/find-maps db textblock-coll {:project-id {$in prj-ids}})
        sorted (sort-by #(:text %) result)]
    (into [] sorted)))

(defn update-textblock [textblock]
  (let [db (db/get-db)]
    (log/info "Text Block: " (pr-str textblock))
    (if (:_id textblock)
      (do
        (mc/update-by-id db textblock-coll (:_id textblock) textblock)
        textblock)
      (mc/insert-and-return db textblock-coll textblock))))


(defn remove-textblock [textblock]
  (let [db (db/get-db)]
    (log/info "Textblock: " (pr-str textblock))
    (mc/remove-by-id db textblock-coll (:_id textblock))))


;;; WS Communication functions

(defmethod event-msg-handler :textblock/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :invoices membership)
     textblocks (list-textblocks-for-user user)]

    [:textblock/list { :code :ok, :data { :result textblocks }}]
    [:textblock/list { :code :error :msg "Error listing knowledge entries"} ]))


(defmethod event-msg-handler :textblock/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     textblock ?data
     has-access? (form-auth/as-form-edit? :invoices membership user-id textblock (get-textblock (:_id ?data)))
     upd-textblock (update-textblock textblock)]

    [:textblock/save { :code :ok, :data { :result upd-textblock }}]
    [:textblock/save { :code :error}]))

(defmethod event-msg-handler :textblock/switch-project
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user    (user-db/get-user user-id)
     old-membership (member/get-membership-by-user user)
     new-membership (member/get-membership (get-in ?data [:project :_id]) user-id)
     textblock (:textblock ?data)
     has-access? (form-auth/as-form-switch-prj?
                   :invoices
                   old-membership
                   new-membership
                   user-id
                   textblock
                   (get-textblock (:_id textblock)))

     upd-textblock (update-textblock (assoc textblock :project-id (:project-id new-membership)))]

    [:textblock/switch-project {:code :ok
                                :data {:result  upd-textblock
                                       :is-visible (project-db/visible-project-id? user (:project-id new-membership))}}]
    [:textblock/switch-project {:code :error}]))

(defmethod event-msg-handler :textblock/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "remove, got: " (pr-str ?data))
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     textblock (get-textblock ?data)
     has-access? (form-auth/as-form-remove? :invoices membership user-id textblock)
     upd-textblock (remove-textblock textblock)]

    [:textblock/remove { :code :ok }]
    [:textblock/remove { :code :error }]))
