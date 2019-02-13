(ns numerix.fields.image-crop
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
  (:require-macros [cljs.core.async.macros  :refer [go go-loop]])
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [taoensso.timbre :as log]
            [cljs.core.async :as async :refer [<! >! chan close! put! pub sub unsub unsub-all]]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d :refer [ObjectId]]
            [numerix.state :as s]
            [numerix.fields.file-upload :as fu]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]
            [robur.events :refer [emit subscribe unsubscribe]]
            [re-frame.core :as rf]))

;(defn make-wrapped-component
;  "Wrap a React component in such a way that the raw JS component is accessible.
;   Useful for for calling methods on the native React component."
;  [component]
;  (fn [attrs & children]
;    (r/create-class
;      {:component-did-mount
;       (fn [this]
;         (when-let [component-ref (:ref attrs)]
;           (reset! component-ref (aget this "refs" "_this"))))
;
;       :reagent-render
;       (fn [attrs & children]
;         (apply
;           r/create-element
;           component
;           (-> attrs
;               (dissoc :ref)
;               (assoc :ref "_this")
;               (clojure.set/rename-keys {:class :className})
;               reagent.impl.component/camelify-map-keys ; maybe best not to use reagent.impl.* functions...
;               reagent.impl.component/map-to-js)
;           children))})))

;(def image-crop-wrap (make-wrapped-component js/react-component))

(defn crop-image-as-blob [cropper callback]
  (let [canvas (.getCroppedCanvas cropper)]
    (.toBlob canvas callback)
  ))

(defn image-crop-edit [& {:keys [aspect-ratio data-uri preview crop-event-name crop-handler]}]
  (let [cropper-id (h/short-rand)
        cropper (r/atom nil)
        crop-event-channel (subscribe crop-event-name
               (fn []
                 (crop-image-as-blob @cropper crop-handler)))]

    (fn []
      (r/create-class
        {
         :component-did-mount
         (fn [_]
           (let [holder (.getElementById js/document cropper-id)
                 opts {
                       :aspectRatio aspect-ratio
                       :preview preview
                       :movable false
                       :rotatable false
                       :scalable false
                       :zoomable false
                       :minCropBoxWidth 50
                       :minCropBoxHeight 50
                       :crop (fn [e]
                               ; Output the result data for cropping image.
                               ;(h/tty-log (.-detail e))
                               ;(let [canvas (.getCroppedCanvas @cropper)
                               ;      uri (.toDataURL canvas)]
                               ;  (reset! cropped-data-uri uri)
                               ;  )
                               )
                               }
                 cropper-elem (js/Cropper. holder (clj->js opts))]
             (reset! cropper cropper-elem)
           ))

         :component-will-unmount
         (fn [_]
           (when @cropper
             (.destroy @cropper))
           (when crop-event-channel
             (unsubscribe crop-event-channel crop-event-name)))

         :reagent-render
         (fn []
           [:div.cropper-container
            [:img {:id cropper-id :src @data-uri}]])

         }))))


(defn image-crop [{:keys [max-size placeholder upload-url image-url-fn on-upload-fn remove-existing-fn] :as props} state]
  (let [data-uri (r/atom nil)
        preview-id (h/short-rand)
        crop-event-name :image-crop/crop-image]
    (fn []
      [:div
       [:div
        (if (empty? @data-uri)
          [:div.file-upload-form
           [:div.row
            [:div.col-12.col-md-5
             [fu/upload-control {:multiple false
                                 :accept-input "image/*"
                                 :accept-match "image/.*"
                                 :max-size max-size
                                 :placeholder (or placeholder "Drop Image")
                                 :pending-files-fn (fn [files]
                                                     (when (> (count files) 0)
                                                       (let [reader (js/FileReader.)]
                                                         (.addEventListener
                                                           reader
                                                           "load" (fn [e]
                                                                    (reset! data-uri (-> e .-target .-result))))
                                                         (.readAsDataURL reader (first files))))
                                                     )
                                 } state]
             ]
            [:div.col-12.col-md-7

             (enc/when-lets [url-fn image-url-fn
                             image-url (url-fn)]
               [:div.image-preview-area
                [:div {:style {:margin-top 15}}
                 [:img.img-circle {:style {:width 150 :height 150 }
                                   :src image-url}]]

                (when remove-existing-fn
                  [:button.btn.btn-link.btn-remove-image
                   {:type "button"
                    :title "Remove Image"
                    :on-click (handler-fn (remove-existing-fn))
                    }
                   [:i.fa.fa-times]])
             ])]
            ]]

          [:div.image-cropper.image-crop-wrap
           [:div.row
            [:div.col-8
             [image-crop-edit
              :crop-event-name crop-event-name
              :data-uri data-uri
              :aspect-ratio 1
              :preview (str "#" preview-id)
              :crop-handler (fn [blob]
                              (swap! state assoc :pending-files [blob])
                              (fu/upload! state (fn [results]
                                                  (reset! data-uri nil)
                                                  (when on-upload-fn
                                                    (on-upload-fn results)))))]
             ]
            [:div.col-4
             [:div {:style {:display :flex
                            :flex-direction :column
                            :justify-content :space-between
                            :align-items :center
                            :width "100%" }}

             [:h1 "Preview"]
             [:div.img-preview.img-circle {:id preview-id
                                            :style {:width "100%" :float "left" :height 150 }}]

             [:div {:style {:display :flex
                            :justify-content :space-between
                            :align-items :flex-start
                            :margin-top "1rem"
                            }}
              [:button.btn.btn-outline-primary
               {:type :button
                :on-click (handler-fn
                            (emit crop-event-name)
                            ;(h/tty-log "getData " (.getData @cropper true))
                            ;(crop-image-as-blob @cropper crop-handler)
                            )
                :style {:margin-right "1rem"}
                } [:i.fa.fa-check] " Upload"]

              [:button.btn.btn-outline-primary
               {:type "button"
                :on-click (handler-fn
                            (swap! state assoc :pending-files nil)
                            (reset! data-uri nil))
                }
               [:i.fa.fa-times] " Reset"]

              ]]]]]
          )]])))

(defn image-crop-field [& {:keys [placeholder
                                  max-size
                                  upload-url
                                  image-url-fn
                                  remove-existing-fn
                                  on-upload-fn] :as props
                           :or   {max-size 200000
                                upload-url "/api/upload"}}]
  (let [csrf-token (rf/subscribe [:post-config-field :csrf-token])
        state (r/atom {
                       :pending-files []
                       :upload-form {:url upload-url
                                     :csrf-token @csrf-token}
                       })]

    (fn []
      [image-crop {:max-size     max-size
                   :placeholder  placeholder
                   :upload-url   upload-url
                   :image-url-fn image-url-fn
                   :remove-existing-fn remove-existing-fn
                   :on-upload-fn on-upload-fn
                   } state])))


(defn profile-image-crop-field [& {:keys [placeholder] :as props
                           :or   {placeholder "Drop Profile Image"}}]

  (r/with-let [user (rf/subscribe [:current-user])]

    [image-crop-field :placeholder placeholder
     :upload-url "/api/profile-image"
     :image-url-fn (fn []
                     (if-let [img-id (:profile-image-id @user)]
                       (str "/api/profile-image/" img-id)
                       nil))
     :on-upload-fn (fn [upload-results]
                     (rf/dispatch [:user/current-user-field-change :profile-image-id
                                   (ObjectId. (:profile-image-id (first upload-results)))])
                     ;(swap! (s/user-data-cursor)
                     ;       assoc
                     ;       :profile-image-id (ObjectId. (:profile-image-id (first upload-results))))
                     )
     :remove-existing-fn (fn []
                           (rf/dispatch [:user/remove-profile-image]))
     ]))


