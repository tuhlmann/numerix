(ns numerix.fields.file-upload
  (:import goog.net.XhrIo
           goog.net.EventType
           goog.events.FileDropHandler
           goog.events.FileDropHandler.EventType
           goog.events.EventType
           goog.dom
           goog.dom.InputType
           goog.dom.TagName
           goog.object
           [goog.events EventType])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [taoensso.timbre :as log]
            [cljs.core.async :as async :refer [<! >! chan close! put! pub sub unsub unsub-all]]
            [numerix.lib.helpers :as h]
            [numerix.state :as s]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]
            [numerix.validation.mime-types :as mime]
            [re-frame.core :as rf]))

(def file-upload-url "/api/upload")

(defn filelist-to-arr [fl]
    (for [idx (range (.-length fl))]
      (.item fl idx)))


(defn append-pending-files [state files]
  (swap! state update :pending-files
         (fn [old-files new-files]
           (into [] (concat old-files new-files))
           ) files))

(defn formdata-append-files [form-data files]
  (doall (map-indexed (fn [idx file]
                 (.append form-data (str "file[" idx "]") file)
                 ) files)))

(defn formdata-append-extra-param [form-data extra-param]
  (h/tty-log "extra param is " extra-param )
  (doall (map (fn [[key value]]
                 (.append form-data (name key) value)
                 ) (seq extra-param))))

;; FIXME Use mime types and validator from cljc
(defn validate-added-files [{:keys [accept-match max-size] :as props
                             :or {accept-match ".*"
                                  max-size (.-MAX_SAFE_INTEGER js/Number)}} files]

  (let [make-error (fn [file msg]
                     {:file file
                      :msg msg
                      })

        [v e] (reduce
                (fn [[valid-accu error-accu] file]
                  (cond
                    (not (-> file .-type (.match accept-match)))
                    [valid-accu (conj error-accu (make-error file (str "This file type is not supported: " (.-type file))))]

                    (>= (.-size file) max-size)
                    [valid-accu (conj error-accu (make-error file (str "The file is too large: " (.-size file) " byte")))]

                    :else
                    [(conj valid-accu file) error-accu]))
                [nil nil] (filelist-to-arr files))
        ]
    [v e]))

(defn send-files!
  ([files io]
   (send-files! files io {}))

  ([files io extra-param]
    (let [csrf-token (rf/subscribe [:post-config-field :csrf-token])
          params (merge {:url file-upload-url :csrf-token @csrf-token} extra-param)
          form-data (doto
                      (js/FormData.)
                      (.append "__anti-forgery-token" (:csrf-token params))
                      (formdata-append-extra-param (dissoc params :url :csrf-token))
                      (formdata-append-files files))]

      (.send io (:url params) "POST" form-data )))
  )

(defn upload-files! [& {:keys [pending-files on-finish-fn extra-param]}]
  (let [upload-state (atom {})
        io (goog.net.XhrIo.)]

    (goog.events.listen io goog.net.EventType.COMPLETE
                        #(log/info "goog.net.EventType.COMPLETE"))

    (goog.events.listen io goog.net.EventType.SUCCESS
                        (fn [_]
                          ;(swap! upload-state assoc :pending-files nil)
                          (let [results (->> io
                                             .getResponseJson
                                             js->clj
                                             (map (fn [m]
                                                    (log/info "map keyword for " (pr-str m))
                                                    (h/keywordize-map m))))]

                            (swap! upload-state assoc :attachments (into [] results))

                            (log/info "attachments" (pr-str (:attachments @upload-state)))
                            (swap! upload-state assoc :status :success)
                            (on-finish-fn @upload-state)
                            )))

    (goog.events.listen io goog.net.EventType.ERROR
                        (fn [_]
                          (log/error "ERROR")
                          (swap! upload-state assoc :status :error)
                          (on-finish-fn @upload-state)
                          ))

    (goog.events.listen io goog.net.EventType.TIMEOUT
                        (fn [_]
                          (log/error "TIMEOUT")
                          (swap! upload-state assoc :status :timeout)
                          (on-finish-fn @upload-state)))

    (send-files! pending-files io extra-param)))


(defn upload!
  ([state]
   (upload! state nil))

  ([state on-success-fn]
    (let [{:keys [upload-form]} @state
          io (goog.net.XhrIo.)]

      (goog.events.listen io goog.net.EventType.COMPLETE
                          #(log/debug "COMPLETE"))

      (goog.events.listen io goog.net.EventType.SUCCESS
                          (fn [_]
                            (swap! state assoc :pending-files nil)
                            (let [results (->> io
                                               .getResponseJson
                                               js->clj
                                               (map (fn [m]
                                                      (log/info "map keyword for " (pr-str m))
                                                      (h/keywordize-map m))))]

                              (swap! state update-in [:attachments] concat results)

                              (log/info "attachments" (pr-str (:attachments @state)))
                              (swap! state assoc :status [:span.success "File uploaded"])
                              (when on-success-fn
                                (on-success-fn results))
                              )))

      (goog.events.listen io goog.net.EventType.ERROR
                          (fn [_]
                            (log/error "ERROR")
                            (swap! state assoc :status [:span.error "Error uploading file"])))

      (goog.events.listen io goog.net.EventType.TIMEOUT
                          (fn [_]
                            (log/error "TIMEOUT")
                            (swap! state assoc :status [:span.timeout "Timeout uploading file"])))

      (send-files! (:pending-files @state) io {:url (:url upload-form) :csrf-token (:csrf-token upload-form)}))))


(defn file-drop-area [{:keys [files-added-fn multiple accept-input] :as props
                       :or {multiple false}} state]
  (let [file-input-id (h/short-rand)]
    (fn []
      (r/create-class
        {
         :component-did-mount
         (fn [_]
           (let [holder (.getElementById js/document file-input-id)]

             (goog.events.listen (goog.events.FileDropHandler. js/document)
                                 goog.events.FileDropHandler.EventType.DROP
                                 (fn [evt]
                                   (h/tty-log "drop event, files is " (.. evt getBrowserEvent -dataTransfer -files))
                                   (files-added-fn (.. evt getBrowserEvent -dataTransfer -files))
                                   ))

             (goog.events.listen holder goog.events.EventType.CHANGE
                                 (fn [evt]
                                   (h/tty-log "change event" evt (.-files holder))
                                   (files-added-fn (.-files holder))
                                   (set! (.-value holder) "")
                                   ))
             ))

         :reagent-render
         (fn []
           (let []
             [:div.file-upload-dropzone
              [:span.drop-files (or (:placeholder props) "Drop files here")]
              [:span.content-divider "or"]
              [:div.input-upload {:on-click (handler-fn (.click (.getElementById js/document file-input-id)))}
               [:i.fa.fa-cloud-upload] (str " Select file" (if multiple "s" ""))
               [:input.btn.btn-outline-primary.select-file-btn {:type "file"
                                                                :id file-input-id
                                                                :style {:display :none}
                                                                :multiple (if multiple "multiple" nil)
                                                                :accept accept-input
                                                                }]
               ]]))
         }))))

(defn upload-control [{:keys [pending-files-fn] :as props } state]
  (let [files-added-fn (fn [files]
                         (h/tty-log "files added" files)
                         (let [[valid-files errors] (validate-added-files props files)]
                           (h/tty-log "valid files " valid-files)
                           (h/tty-log "error files " errors)
                           (append-pending-files state valid-files)
                           (pending-files-fn (:pending-files @state)))
                         )]
    (fn []
      (r/create-class
        {
         :component-did-mount
         (fn [_])

         :reagent-render
         (fn []
           (let []
             [file-drop-area (merge props {:files-added-fn files-added-fn}) state]))

         }))))


(defn file-upload-control [props state]
  (let [outer-pending-files-fn (:pending-files-fn props)
        outer-remove-existing-fn (:remove-existing-fn props)]
    (fn []
      (let [{:keys [pending-files]} @state
            {:keys [existing-files]} props]
        [:div.file-upload-form
         (when-not (and (empty? pending-files) (empty? existing-files))
           [:div.margin-top-05
            [:table.table.table-hover
             [:thead
              [:tr [:th] [:th "Name"] [:th "Size"] [:th "Uploaded"] [:th]]]
             [:tbody
              (doall
                (map-indexed (fn [idx f]
                               ^{:key (str "p-" idx)}
                               [:tr
                                [:td [mime/type-to-icon (.-type f)]]
                                [:td (.-name f)]
                                [:td (h/readable-file-size (.-size f))]
                                [:td [:span.text-danger "Pending"]]
                                [:td [:button.btn.btn-link.btn-sm.link-danger
                                      {:type :button
                                       :on-click (handler-fn
                                                   (swap! state update :pending-files
                                                          (fn [pending-files]
                                                            (h/vec-remove pending-files idx)))
                                                   (when (fn? outer-pending-files-fn)
                                                     (outer-pending-files-fn (:pending-files @state))))}
                                      [:i.fa.fa-times]]]]) pending-files))
              (doall
                (map-indexed (fn [idx file]
                               ^{:key (str "a-" idx)}
                               [:tr
                                [:td [mime/type-to-icon (:contentType file)]]
                                [:td [:a.file-list-entry-filename {:href (str "/api/file/" (:_id file))
                                                                   :target "_blank"} (:filename file)]]
                                [:td (h/readable-file-size (:length file))]
                                [:td (-> file :uploadDate js/Date. .toDateString)]
                                [:td
                                 [:button.btn.btn-link.btn-sm.link-danger
                                  {:type :button
                                   :title "Remove File"
                                   :on-click (handler-fn
                                               (swap! state update :files-to-remove
                                                      (fn [to-remove]
                                                        (if (some #(= % (:_id file)) to-remove)
                                                          (filterv #(not= % (:_id file)) to-remove)

                                                          (into [(:_id file)] to-remove))))
                                               (when (fn? outer-remove-existing-fn)
                                                 (outer-remove-existing-fn (:files-to-remove @state))))
                                   }
                                  (if (some #(= % (:_id file)) (:files-to-remove @state))
                                    [:i.fa.fa-refresh]
                                    [:i.fa.fa-trash])]]
                                ]) existing-files))

              ]]])
         [:div
          (:status @state)
          [upload-control (merge props {:pending-files-fn
                                        (fn [added-files]
                                          ;(let [filename (:filename @state)]
                                          ;  (log/info "upload-field change |" name "| |" filename "|")
                                          ;  (swap! state assoc :file name)
                                          ;  (when (or (= filename file)
                                          ;            (str/blank? filename))
                                          ;    (swap! state assoc :filename name))
                                          ;  )
                                          (when (fn? outer-pending-files-fn)
                                            (outer-pending-files-fn added-files)))
                      }) state]
          ]
           ;[:button.btn.btn-primary
           ; {:on-click (handler-fn
           ;              (log/info "calling upload now")
           ;              (upload! state))
           ;  :type :button
           ;  :disabled (when (empty? pending-files) "disabled")
           ;  :style {:margin-top "1rem" :margin-bottom "1rem"}
           ;  }
           ; "Upload"]
         ]))))

(defn file-upload-field [& {:keys [multiple
                                   accept-input
                                   accept-match
                                   existing-files
                                   pending-files-fn
                                   remove-existing-fn] :as props
                            :or {accept-input "*/*"
                                 accept-match ".*/.*"}}]
  (let [csrf-token (rf/subscribe [:post-config-field :csrf-token])
        state (r/atom {
                       :pending-files nil
                       :files-to-remove nil
                       ;:attachments []
                       :upload-form {
                                     :url "/api/upload"
                                     :csrf-token @csrf-token
                                     }
                       :status [:span ""]
                       })]

    (fn []
      [file-upload-control {:multiple multiple
                            :accept-input accept-input
                            :accept-match accept-match
                            :existing-files existing-files
                            :pending-files-fn pending-files-fn
                            :remove-existing-fn remove-existing-fn
                            } state])))

(defn file-list [files]
  (fn []
    [:div.row.file-list
     [:div.col-12
      (map-indexed
        (fn [idx file]
          ^{:key idx}
          [:div.file-list-entry
           [:span.file-list-entry-mime [mime/type-to-icon (:contentType file)]]
           [:a.file-list-entry-filename {:href (str "/api/file/" (:_id file))
                                         :target "_blank"} (:filename file)]
           ]
          ) files)
      ]]))
