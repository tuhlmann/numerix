(ns numerix.views.documents
  (:require-macros [reagent.ratom :refer [reaction]])  ;; reaction is a macro
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [re-frame.db :as db]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.api.document :as document-api]
            [numerix.events.common]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d :refer [ObjectId]]
            [numerix.site :as site]
            [cljs-time.core :refer [now days minus day-of-week]]
            [cljs-time.format :refer [formatter formatters parse unparse]]
            [clojure.string :as str]
            [re-com.core :refer [row-button single-dropdown v-box h-box md-circle-icon-button
                                 datepicker datepicker-dropdown]
             :refer-macros [handler-fn]]
            [numerix.fields.file-upload :as file-upload :refer [file-upload-field]]
            [validateur.validation :as v]
            [numerix.api.cache :as cache]
            [numerix.api.crud-ops :as crud-ops]
            [numerix.api.tag :as tag-api]
            [taoensso.encore :as enc]
            [re-frame.core :as rf]))


(defn document-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Title"    :value (:title @form-data)]
       [f/static-field :label "Summary"  :type :quill :value (:summary @form-data)]
       [f/static-field :label "Tags"     :type :tag :value (:tags @form-data)]
       [f/static-field :label "Created"  :value (h/format-date "dd.MM.yyyy" (:created @form-data))]
       ;[f/static-field :label "Type"     :value (:content-type @form-data)]
       [f/static-field :label "Attachments" :type :file-upload :value (:__attachments @form-data)]

       ])))


(defn set-empty-title [current-title pending-files]
  (when-not (seq current-title)
    (enc/if-let [file (first pending-files)
                 name (.-name file)]
                (do
                   (log/info "set title to " name)
                   name)
                nil)))

(defn document-detail-edit-inner []
  (let [form-state (rf/subscribe [:form-state])
        form-data (rf/subscribe [:form-data])
        user-tags (tag-api/list-project-tags)]
    (fn []
      [:form
       [f/pfield :label "Title"   :path :title]
       [f/pfield :label "Summary" :path :summary :type :quill]
       [f/pfield :label "Tags"    :path :tags :type :tag :categories user-tags]

       (if-not (or (:allow-form-upload @form-data true) (:allow-form-remove @form-data true))
         [f/static-field :label "Attachments" :type :file-upload :value (:__attachments @form-data)]

         [f/pfield :label "Attachments" :type :file-upload :path :__attachments :multiple true
          :accept-input "*/*"
          :accept-match ".*/.*"
          :existing-files (:__attachments @form-data)
          :pending-files-fn (fn [pending-files]
                              (rf/dispatch [:assoc-in-form-state :pending-files pending-files])
                              (when-let [new-title (set-empty-title (:title @form-data) pending-files)]
                                (rf/dispatch [:form-data-field-change :title new-title])))

          :remove-existing-fn (fn [files-to-remove]
                                (rf/dispatch [:assoc-in-form-state :files-to-remove files-to-remove]))

          ])
       ])))


(defn document-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        document-data (if (:add-new @form-state) (document-api/new-document-rec @user) (:selected-item @form-state {}))
        form-config {:mode          :edit
                     :title         [:span "Add Document"]
                     :title-subtext [:span "Add a new Document"]
                     :card-title    [:span "Document " (:name document-data)]
                     :inner-form    document-detail-edit-inner
                     :form-data     document-data
                     :validation (v/validation-set
                                   (v/length-of :title :within (range 3 200) :blank-message "Please provide a title"))

                     :buttons {
                               :project-switch {:tt "Switch Project"
                                                :handler (fn [form-data project]
                                                           (rf/dispatch [:crud-ops/switch-project
                                                                         {:type :document
                                                                          :record form-data
                                                                          :project project}]))}
                               :cancel {
                                        :tt      "Reset Document Data"
                                        :handler (fn [_]
                                                   (rf/dispatch [:common/show-details-view-item false]))}

                               :save   {
                                        :tt      "Save Document Data"
                                        :handler (fn [form-data]
                                                   (log/debug "save document " (pr-str (:pending-files form-state)))
                                                   (rf/dispatch
                                                     [:crud-ops/save
                                                      {:type :document
                                                       :record form-data
                                                       :next-fn
                                                       (fn [new-record on-success]
                                                         (if-not (and
                                                                   (empty? (:pending-files @form-state))
                                                                   (empty? (:files-to-remove @form-state)))
                                                           (do
                                                             (log/debug "Have pending files, upload them or remove")
                                                             (file-upload/upload-files!
                                                               :pending-files (:pending-files @form-state)
                                                               :extra-param
                                                               {:files-to-remove (clj->js (:files-to-remove @form-state []))
                                                                :related-id (:_id new-record)
                                                                :related-type "document"}
                                                               :on-finish-fn
                                                               (fn [result]
                                                                 (log/debug "GOT RESULT " (pr-str result))
                                                                 ;(swap! form-state dissoc :pending-files :files-to-remove)
                                                                 (rf/dispatch [:dissoc-in-form-state :pending-files :files-to-remove])
                                                                 (when (= (:status result) :success)
                                                                   (rf/dispatch [:crud-ops/get {:type :document
                                                                                                :record new-record}]))
                                                                 )))

                                                           (on-success new-record)
                                                           )
                                                         )}]))}}}]

    ;; attachments anhängen:
    ;; - files hochladen
    ;; - success upload file-ids in record eintragen
    ;; - Rückmeldung an client das min 1 datei hinzugefügt wurde
    ;; - record neu anfordern
    ;; - im Backend werden die file-ids durch informationen ergänzt

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn document-detail-view [item]
  (let [form-config {:mode :view
                     :title         [:span "Document"]
                     :title-subtext [:span "View Document"]
                     :card-title    [:span "Document "]
                     :inner-form    document-detail-view-inner
                     :form-data     item

                     :buttons       (fn [form-data]
                                      (let [common
                                            {:edit {
                                                    :tt      "Edit Document"
                                                    :handler (fn [_]
                                                               (rf/dispatch [:common/show-details-edit-item true]))}

                                             :project-view {:tt "Current Project"}}]

                                        (if (:allow-remove form-data true)
                                          (merge
                                            common
                                            {:remove {:tt "Remove Document"
                                                      :handler (fn [form-data]
                                                                 (rf/dispatch [:crud-ops/remove
                                                                               {:type :document
                                                                                :record form-data}]))}})

                                          common)))
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn [item]
      [c/generic-form])))

(defn document-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [document-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [document-detail-view list-item]
          [c/no-item-found "Document"])

        :else
        [:div]))))


;;; Main entry for company master-detail page
(defn documents-page []
  (let [page-config
        {
         :form-state {
                      :form-name :documents
                      :edit-item false
                      :show-details false
                      :is-loading       true
                      :mouse-inside-detail-area-no-follow false
                      }

         :master-config {
                         :full-text-fn (fn [i] (str (:filename i) " " (:content-type i) " "
                                                    (h/format-date "dd.MM.yyyy" (:created i)) " "
                                                    (str/lower-case (:comment i ""))))

                         :inner-master-list-item c/master-list-item-std
                         :inner-master-list-item-desc {
                                                       :title (fn [v] [:h6 (str (or (:filename v) (:title v)) ", created "
                                                                              (h/format-date "dd.MM.yyyy" (:created v)))])
                                                       ;:description [:span ""]
                                                       ;:img-url "/img/portfolio/submarine.png"
                                                       :img-icon "fa fa-files-o fa-2x"}

                         :master-list-item-std-detail-link #(site/document-route {:documentId (.toString (:_id %))})
                         :master-list-link #(site/documents-route)
                         :detail-area-main document-detail-area
                         :allow-create-records true}
         }]


    (rf/dispatch-sync [:init-documents-page page-config])
    (fn []
      [c/master-detail-page])))

(rf/reg-event-fx
  :init-documents-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    {:db (-> db
             (s/assoc-master-config master-config)
             (s/assoc-form-state (merge (s/form-state db) form-state)))

     :load-and-cache [:documents/list 1000]}))

