(ns numerix.model.invoice
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.model.user :as user]
            [numerix.model.invoice_details :as invoice-details]
            [numerix.model.contact :as contact]
            [numerix.model.files :as files]
            [numerix.model.textblock :as textblock]
            [numerix.components.pdf-renderer :as pdf-renderer]
            [numerix.config :refer [C]]
            [numerix.formatters.invoice :as invf]
            [numerix.lib.helpers :as h]
            [robur.events :refer [emit subscribe]]
            [numerix.components.ws :as ws]
            [taoensso.encore :as enc]
            [clj-time.core :refer [now]]
            [numerix.model.project :as prj]
            [numerix.model.document :as document]
            [numerix.model.membership :as member]
            [numerix.model.user-db :as user-db]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth]))

(def invoice-coll (:invoice db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'invoices'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db invoice-coll)
      (mc/create db invoice-coll {})
      (mc/ensure-index db invoice-coll (array-map :author-id 1) { :unique false })
      (mc/ensure-index db invoice-coll (array-map :project-id 1) { :unique false }))))

;; Low level, DB access functions

(defn get-invoice [id]
  (enc/when-let [_ id
                 db (db/get-db)]
    (mc/find-map-by-id db invoice-coll id)))

(defn list-invoices-for-user [user]
  (let [db (db/get-db)
        prj-ids (project-db/visible-project-ids user)
        result (mc/find-maps db invoice-coll {:project-id {$in prj-ids}})
        sorted (sort-by #(:name %) result)]
    (into [] sorted)))

(defn initial-counter [pattern]
  (let [counter-seq (vec (re-seq #".*\{counter\:(\d)\}.*" pattern))
        [[match counter]] counter-seq]
    (try
      (. Integer parseInt (or counter "0"))
      (catch Exception e 0))))

(defn next-invoice-no
  "Will read the defined pattern and fill in the blanks for this user.
  Reads and increases the counter atomically from a invoice-details collection.
  If no pattern is defined will just use the incremented counter.
  If no counter is found, 0 is assumed (and incremented, the first value would be 1)"
  [user-id pattern]

  (let [initial-counter (initial-counter pattern)
        incremented-counter (invoice-details/increment-invoice-value user-id :counter initial-counter)
        with-counter (.replaceAll pattern "\\{counter.*?\\}" (str incremented-counter))]

    with-counter))

(defn create-invoice [user invoice]
  (let [db (db/get-db)
        invoice-no (next-invoice-no (:author-id invoice) (:invoice-no-pattern user "{counter}"))
        invoice-with-no (assoc invoice :invoice-no invoice-no)]

    ;(log/info "Create Invoice: " (pr-str invoice-with-no))

    (mc/insert-and-return db invoice-coll invoice-with-no)))

(defn update-invoice [invoice]
  (let [db (db/get-db)]
    (mc/update-by-id db invoice-coll (:_id invoice) invoice)
    invoice))


(defn remove-invoice [invoice]
  (let [db (db/get-db)]
    (enc/when-let [invoice-pdf (:invoice-pdf invoice)
                   document (document/get-raw-document (:document-id invoice-pdf))]
      (document/remove-document document))
    (mc/remove-by-id db invoice-coll (:_id invoice))))


(defn replace-pdf
  "Adds or replaces the document in this record with the new one.
  Will remove the old document from GridFS after successful update."
  [invoice document-rec file-rec]

  ;(log/info "replace-pdf called " (pr-str invoice) " \n\n " (pr-str document-rec))

  (let [db (db/get-db)
        re (mc/update-by-id db invoice-coll (:_id invoice)
                      {"$set" {:invoice-pdf {
                                             :document-id (:_id document-rec)
                                             :document-attachment-id (:_id file-rec)
                                             :created (:uploadDate file-rec)
                                             }}}
                      {:multi false})]

    ; TODO: Do not remove the document
    ;(enc/when-lets [success (.isUpdateOfExisting re)
    ;                old-doc-id (get-in invoice [:invoice-pdf :document-id])]
    ;               (files/remove-file-by-id old-doc-id))

    (get-invoice (:_id invoice))))

(defn get-or-create-document [& {:keys [author-id invoice] :as props}]

  (enc/if-lets [document-id (get-in invoice [:invoice-pdf :document-id] nil)
                document (document/get-raw-document document-id)]

               (do
                 (log/info "document in invoice")
                 document)

               (do
                 (log/info "no document in invoice, creating a new one.")
                 (let [d {
                          :title (str "Documents for Invoice " (:invoice-no invoice))
                          :tags ["invoice"]
                          :author-id author-id
                          :invoice-id (:_id invoice)
                          :project-id (:project-id invoice)
                          :created (now)
                          :allow-form-upload false
                          :allow-form-remove false
                          :allow-remove false
                          :attachments []}]

                   (document/update-document author-id d)))
               )

  )

(defn create-or-update-document [& {:keys [author-id invoice byte-array]}]
  (enc/when-lets [document (get-or-create-document :author-id author-id :invoice invoice :byte-array byte-array)
                  doc-meta {:author-id    author-id
                            :project-id   (:project-id invoice)
                            :filename     (str "invoice-" (h/format-date "yyyy-MM-dd-HH-mm-ss" (now)) ".pdf")
                            :metadata     {:author-id    author-id
                                           :related-id (:_id document)
                                           :related-type "document"
                                           }
                            :content-type "application/pdf"}
                  file-record (document/save-file-bytes byte-array doc-meta false)
                  upd-document (update document :attachments #(into [(:_id file-record)] %))]

                 [(document/update-document author-id upd-document) file-record]
                 ;; FIXME Should push an update for the changed document
  ))


;;; WS Communication functions

(defmethod event-msg-handler :invoice/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :invoices membership)
     invoices (list-invoices-for-user user)]

    [:invoice/list { :code :ok, :data { :result invoices }}]
    [:invoice/list { :code :error :msg "Error listing invoices"} ]))


(defmethod event-msg-handler :invoice/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [existing-invoice? (boolean (:_id ?data))]
    (auth-let
      ?reply-fn
      [user-id (config/get-auth-user-id-from-req ring-req)
       user    (user-db/get-user user-id)
       membership (member/get-membership-by-user user)
       invoice ?data
       has-access? (form-auth/as-form-edit? :invoices membership user-id invoice (get-invoice (:_id ?data)))
       upd-invoice (if existing-invoice?
                     (update-invoice invoice)
                     (create-invoice user invoice))]

      [:invoice/save { :code :ok, :data { :result upd-invoice }}]
      [:invoice/save { :code :error}])))

(subscribe :invoice/invoice-pdf-updated
           (fn [{:keys [data]}]
             (ws/send-all-user-presences! (C :ws-connection) (:identity data) [:invoice/invoice-pdf-updated (:invoice data)])))

(defmethod event-msg-handler :invoice/print
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     identity (config/get-identity-from-req ring-req)
     user    (user-db/get-user user-id)
     existing-invoice? (boolean (:_id ?data))
     membership (member/get-membership-by-user user)
     invoice ?data
     has-access? (form-auth/as-form-edit? :invoices membership user-id invoice (get-invoice (:_id ?data)))

     _ (pdf-renderer/render-document
         (C :pdf-renderer)
         {
          :document-ready-fn     (fn [doc-bytes]
                                   ;; receives the bytes of the rendered document
                                   ;(log/info "document bytes: " (pr-str doc-bytes))
                                   (when-let [[document file-record] (create-or-update-document
                                                                       :author-id user-id
                                                                       :invoice invoice
                                                                       :byte-array doc-bytes)]

                                     (let [upd-invoice (replace-pdf invoice document file-record)]
                                       ;(log/info "updated invoice is " upd-invoice)
                                       (emit :invoice/invoice-pdf-updated {:identity identity :invoice upd-invoice}))))

          :user                  (user-db/remove-secure-fields user)
          :invoice               (merge (invf/render-invoice-values invoice)
                                        {
                                         :invoice-date-str  (h/format-date "dd.MM.yyyy" (:invoice-date invoice))
                                         :delivery-date-str (h/format-date "dd.MM.yyyy" (:delivery-date invoice))
                                         })
          :contact               (contact/get-contact (:companyId invoice))
          :textblocks            (when (:textblockIds invoice)
                                   (textblock/find-textblocks (:textblockIds invoice)))

          ;:format-date ^{:stencil/pass-context true}
          ;          (fn [raw context]
          ;            (log/info "received " (pr-str raw) " " (pr-str context))
          ;            (str raw))
          })]


    [:invoice/print { :code :ok, :data { :invoice invoice } :msg "Invoice is being created..."}]
    [:invoice/print { :code :error :msg "We could not create a printable pdf."}]))


(defmethod event-msg-handler :invoice/switch-project
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user    (user-db/get-user user-id)
     old-membership (member/get-membership-by-user user)
     new-membership (member/get-membership (get-in ?data [:project :_id]) user-id)
     invoice (:invoice ?data)
     has-access? (form-auth/as-form-switch-prj?
                   :invoices
                   old-membership
                   new-membership
                   user-id
                   invoice
                   (get-invoice (:_id invoice)))

     upd-invoice (update-invoice (assoc invoice :project-id (:project-id new-membership)))]

    [:invoice/switch-project {:code :ok
                              :data {:result  upd-invoice
                                     :is-visible (project-db/visible-project-id? user (:project-id new-membership))}}]
    [:invoice/switch-project {:code :error}]))

(defmethod event-msg-handler :invoice/remove
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     invoice (get-invoice ?data)
     has-access? (form-auth/as-form-remove? :invoices membership user-id invoice)

     upd-invoice (remove-invoice invoice)]

    [:invoice/remove { :code :ok }]
    [:invoice/remove { :code :error }]))

