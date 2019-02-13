(ns numerix.model.document
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [mongoruff.collection :as ruff]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [monger.gridfs :refer [store-file make-input-file filename content-type metadata]]
            [clojure.string :as str :refer [join]]
            [numerix.model.files :as files]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [numerix.lib.helpers :as h]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.model.user :as db-user]
            [numerix.model.tag :as tags]
            [taoensso.encore :as enc]
            [numerix.model.project :as prj]
            [numerix.model.user :as user]
            [numerix.model.user-db :as user-db]
            [numerix.model.project-db :as project-db]
            [numerix.model.membership :as member]
            [numerix.auth.form-auth :as form-auth])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]
           (com.mongodb.gridfs GridFS)
           (java.io ByteArrayInputStream FileInputStream)))

(def document-coll (:document db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'documents'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db document-coll)
      (mc/create db document-coll {})
      (mc/ensure-index db document-coll (array-map :author-id 1) { :unique false})
      (mc/ensure-index db document-coll (array-map :project-id 1) { :unique false }))))

(defn before-save [user-id project-id record]
  (->> record
       (h/strip-augments)
       (tags/lower-case-tags)
       (tags/check-new-tags user-id project-id)))


;; Low level, DB access functions
(defn save-file-stream [ins document-meta create-document-rec?]
  (let [db (db/get-db)
        fs (files/get-gridfs db)
        saved (store-file (make-input-file fs ins)
                          (filename (:filename document-meta))
                          (metadata (:metadata document-meta))
                          (content-type (:content-type document-meta)))
        ;d {
        ;   :author-id (:author-id document-meta)
        ;   :project-id (:project-id document-meta)
        ;   :document-id (:_id saved)
        ;   :created (:uploadDate saved)
        ;   :filename (:filename saved)
        ;   :content-type (:contentType saved)}
        ]

    ; FIXME Remove that part. save-file should not create a document
    (if create-document-rec?
      (do
        (log/info "Saved file " (pr-str saved))
        ;(mc/insert-and-return db document-coll d)
        saved)

      saved))
  )


(defn save-file-bytes
  "Saves a file to GridFS. Optionally creates a record in the documents collection.
  If a documents record is created, this is returned. Otherwise we return the GridFS record."
  ([byte-arr document-meta]
   (save-file-bytes byte-arr document-meta true))

  ([byte-arr document-meta create-document-rec?]
   (let [is (ByteArrayInputStream. byte-arr)]
     (save-file-stream is document-meta create-document-rec?))))


(defn save-file
  "Saves a file to GridFS. Optionally creates a record in the documents collection.
  If a documents record is created, this is returned. Otherwise we return the GridFS record."
  ([file document-meta]
   (save-file file document-meta true))

  ([file document-meta create-document-rec?]
   (let [is (FileInputStream. file)]
     (save-file-stream is document-meta create-document-rec?))))

(defn add-attachments [rec]
  (enc/if-lets [r rec
                file-ids (:attachments r)]

               (assoc r :__attachments (files/get-mult-files-info file-ids))

               rec))

(defn get-raw-document [id]
  (enc/when-let [_ id
                 db (db/get-db)
                 oid (if (string? id) (ObjectId. id) id)]
    (mc/find-map-by-id db document-coll oid)))

(defn get-document [id]
  (-> (get-raw-document id)
      (add-attachments)))

(defn list-documents-for-user [user]
  (let [db (db/get-db)
        prj-ids (project-db/visible-project-ids user)
        result (mc/find-maps db document-coll {:project-id {$in prj-ids}})
        sorted (sort-by #(:text %) result)
        sorted (map add-attachments sorted)]
    (into [] sorted)))

(defn update-document [user-id document]
  (let [db (db/get-db)]
    (if (:_id document)
      (do
        (mc/update-by-id db document-coll (:_id document) (before-save user-id (:project-id document) document))
        document)
      (mc/insert-and-return db document-coll (before-save user-id (:project-id document) document)))))


(defn remove-document [document]
  (let [db (db/get-db)]
    (log/info "remove document " document)
    (files/remove-mult-files-by-id (:attachments document))
    (mc/remove-by-id db document-coll (:_id document))))


(defn attach-files [user-id related-id file-ids]
  (when-let [document (get-document related-id)]
    ;(log/info "found related document " (join ", " (map str (:attachments document))))
    (let [upd-document (assoc document :attachments (concat (:attachments document) file-ids))]
      ;(log/info "attached files " (join ", " (map str (:attachments upd-document))))
      (update-document user-id upd-document))))

(defn remove-files [user-id related-id file-ids]
  (when-let [document (get-document related-id)]
    ;(log/info "REMOVE found related document " (join ", " (map str (:attachments document))))
    (let [upd-document (update document :attachments
                               (fn [att]
                                 (filterv (fn [id]
                                            (not-any? #(= id %) file-ids)) att)))]
      ;(log/info "REMOVE: attached files " (join ", " (map str (:attachments upd-document))))
      (update-document user-id upd-document))))

;;; WS Communication functions

(defmethod event-msg-handler :document/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :documents membership)
     documents (list-documents-for-user user)]

    [:document/list { :code :ok, :data { :result documents }}]
    [:document/list { :code :error :msg "Error listing documents"} ]))


(defmethod event-msg-handler :document/get
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     has-access? (form-auth/as-form-read? :documents membership)
     document (get-document (:_id ?data))]

    [:document/get { :code :ok :data { :result document }}]
    [:document/get { :code :error }]))


(defmethod event-msg-handler :document/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "Save document " (pr-str ?data))
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     document ?data
     has-access? (form-auth/as-form-edit? :documents membership user-id document (get-document (:_id ?data)))
     upd-document (update-document user-id document)]

    [:document/save { :code :ok :data { :result upd-document }}]
    [:document/save { :code :error }]))


(defmethod event-msg-handler :document/switch-project
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  ;(log/info "document/switch-project " ?data)
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user    (user-db/get-user user-id)
     old-membership (member/get-membership-by-user user)
     new-membership (member/get-membership (get-in ?data [:project :_id]) user-id)
     document (:document ?data)
     has-access? (form-auth/as-form-switch-prj?
                   :documents
                   old-membership
                   new-membership
                   user-id
                   document
                   (get-document (:_id document)))

     upd-document (update-document user-id (assoc document :project-id (:project-id new-membership)))]

    [:document/switch-project {:code :ok
                               :data {:result upd-document
                                      :is-visible (project-db/visible-project-id? user (:project-id new-membership))}}]
    [:document/switch-project {:code :error}]))



(defmethod event-msg-handler :document/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     document (get-document ?data)
     has-access? (form-auth/as-form-remove? :documents membership user-id document)
     upd-document (remove-document document)]

    [:document/remove { :code :ok }]
    [:document/remove { :code :error }]))
