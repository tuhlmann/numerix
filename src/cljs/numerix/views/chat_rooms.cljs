(ns numerix.views.chat-rooms
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [re-frame.db :as db]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.fields.comments :as comments]
            [numerix.views.base :as base]
            [numerix.fields.quill :as quill]
            [numerix.events.common]
            [numerix.lib.helpers :as h]
            [numerix.site :as site]
            [clojure.string :as str]
            [numerix.lib.datatypes :as d]
            [clojure.data]
            [cljs-time.core :as t :refer [now days minus day-of-week]]
            [cljs-time.format :refer [formatter formatters parse unparse]]
            [clojure.string :as str]
            [re-com.core :refer [row-button single-dropdown v-box h-box md-circle-icon-button
                                 datepicker datepicker-dropdown]
             :refer-macros [handler-fn]]
            [numerix.api.cache :as cache]
            [validateur.validation :as v]
            [numerix.history :as history]
            [numerix.fields.tag-input :as tag]
            [cuerdas.core :as cue]
            [numerix.api.crud-ops :as crud-ops]
            [numerix.api.tag :as tag-api]
            [numerix.fields.selecter :as selecter]
            [re-frame.core :as rf]
            [numerix.fields.chat-room :as chat-room]))


(defn chat-room-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Title" :value (:name @form-data)]
       [f/static-field :label "Tags" :type :tag :value (:tags @form-data)]
       [chat-room/chat-room @form-data]
       ])))

(defn chat-room-detail-edit-inner []
  (let [form-data (rf/subscribe [:form-data])
        ;; FIXME: Subscribe to project-tags
        project-tags (tag-api/list-project-tags)]
    (fn []
      [:div.form
       [f/pfield :label "Title" :path :name]
       [f/pfield :label "Tags" :path :tags :type :tag :categories project-tags]
       ])))


(defn chat-room-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        chat-room (if (:add-new @form-state) (crud-ops/new-record @user) (:selected-item @form-state {}))
        form-config {:mode       :edit
                     :title      [:span "Add Chat Room"]
                     :inner-form chat-room-detail-edit-inner
                     :form-data  chat-room
                     :validation (v/validation-set
                                   (v/length-of :name :within (range 3 200) :blank-message "Please provide a title"))

                     :buttons {
                               :cancel {
                                        :tt      "Reset Chat Room"
                                        :handler (fn [_]
                                                   (rf/dispatch [:common/show-details-view-item true]))}

                               :save   {
                                        :tt      "Save Chat Room"
                                        :handler (fn [form-data]
                                                   (rf/dispatch [:crud-ops/save {:type :chat-room
                                                                                 :record form-data}]))
                                        }

                               :project-switch {:tt "Switch Project"
                                                :handler (fn [form-data project]
                                                           (rf/dispatch [:crud-ops/switch-project
                                                                         {:type :chat-room
                                                                          :record form-data
                                                                          :project project}])) }

                               :remove {
                                        :tt      "Remove Chat Room"
                                        :handler (fn [form-data]
                                                   (rf/dispatch [:crud-ops/remove {:type :chat-room
                                                                                   :record form-data}]))
                                        }
                               }}]




    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn chat-room-detail-view [item]
  (let [form-config {:mode :view
                     :title         [:span "Chat Room"]
                     :title-subtext (fn [i]
                                      [:span [:span "View Chat Room"]
                                       [:span.pull-right
                                        [tag/tag-list-mini (:tags i)]]])

                     ;:card-title    Do not define a title
                     :inner-form    chat-room-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :project-view {:tt "Current Project"}

                                     :edit {
                                            :tt "Edit Chat Room"
                                            :handler (fn [_]
                                                       (rf/dispatch [:common/show-details-edit-item true])
                                                       )
                                            }

                                     :remove {
                                              :tt "Remove Chat Room"
                                              :handler (fn [form-data]
                                                         (log/info "Remove record ")
                                                         (rf/dispatch [:crud-ops/remove {:type :chat-room
                                                                                         :record form-data}]))}}}]




    (rf/dispatch-sync [:init-form-config form-config])
    (fn [_]
      [c/generic-form])))

(defn chat-room-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [chat-room-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [chat-room-detail-view list-item]
          [c/no-item-found "Chat"])

        :else
        [:div "don't know what to do "]))))


;;; Main entry for company master-detail page
(defn chat-room-page []
  (let [page-config
        {:form-state    {
                         :form-name         :chat-room
                         :edit-item         false
                         :show-details      false
                         :is-loading        true
                         :mouse-inside-detail-area-no-follow false
                         }

         :master-config {
                         :full-text-fn                    (fn [i] (str (str/lower-case (:name i)) " "
                                                                       (str/lower-case (:text i ""))))
                         :inner-master-list-item           c/master-list-item-std
                         :inner-master-list-item-desc      {
                                                            :title    (fn [v] [:div
                                                                               [:div.col-12.col-sm-4.col-lg-3
                                                                                (:name v)]
                                                                               [:div.col-12.col-sm-8.col-lg-9
                                                                                (h/shorten 100 "..." (cue/strip-tags (:text v)))]])

                                                            :img-icon "fa fa-comments-o"
                                                            ;:on-click c/select-item-and-edit-details
                                                            }

                         :master-list-item-std-detail-link #(site/chat-room-route {:chat-room-id (.toString (:_id %))})
                         :master-list-link                 #(site/chat-rooms-route)
                         :detail-area-main                 chat-room-detail-area
                         }}]

    (rf/dispatch-sync [:init-chat-room-page page-config])
    (fn []
      [c/master-detail-page])))


;; Initial meetings page state
(rf/reg-event-fx
  :init-chat-room-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    {:db (-> db
             (s/assoc-master-config master-config)
             (s/assoc-form-state (merge (s/form-state db) form-state)))

     :load-and-cache [:chat-room/list 100]}))

