(ns numerix.views.meetings
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
            [re-frame.core :as rf]))


(defn meetings-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Title" :value (:name @form-data)]
       [f/static-field :type :quill :value (:text @form-data)]
       [f/static-field :label "Tags" :type :tag :value (:tags @form-data)]
       ;[chat-room/chat-room]
       [comments/comments-container]
       ])))

(defn meetings-detail-edit-inner []
  (let [form-data (rf/subscribe [:form-data])
        ;; FIXME: Subscribe to project-tags
        project-tags (tag-api/list-project-tags)]
    (fn []
      [:div.form
       [f/pfield :label "Title" :path :name]
       [f/pfield :path :text :type :quill]
       [f/pfield :label "Tags" :path :tags :type :tag :categories project-tags]
       ])))


(defn meetings-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        meeting (if (:add-new @form-state) (crud-ops/new-record @user) (:selected-item @form-state {}))
        form-config {:mode       :edit
                     :title      [:span "Add Meeting"]
                     :inner-form meetings-detail-edit-inner
                     :form-data  meeting
                     :validation (v/validation-set
                                   (v/length-of :name :within (range 3 200) :blank-message "Please provide a title"))

                     :buttons {
                               :cancel {
                                        :tt      "Reset Meeting"
                                        :handler (fn [_]
                                                   (rf/dispatch [:common/show-details-view-item true]))}

                               :save   {
                                        :tt      "Save Meeting"
                                        :handler (fn [form-data]
                                                   (rf/dispatch [:crud-ops/save {:type :meeting
                                                                                 :record form-data}]))
                                        }

                               :project-switch {:tt "Switch Project"
                                                :handler (fn [form-data project]
                                                           (rf/dispatch [:crud-ops/switch-project
                                                                         {:type :meeting
                                                                          :record form-data
                                                                          :project project}])) }

                               :remove {
                                        :tt      "Remove Knowledge Base Entry"
                                        :handler (fn [form-data]
                                                   (rf/dispatch [:crud-ops/remove {:type :meeting
                                                                                   :record form-data}]))
                                        }
                               }}]




    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn meetings-detail-view [item]
  (let [form-config {:mode :view
                     :title         [:span "Meeting Minutes"]
                     :title-subtext (fn [i]
                                      [:span [:span "View Meeting"]
                                       [:span.pull-right
                                        [tag/tag-list-mini (:tags i)]]])

                     ;:card-title    Do not define a title
                     :inner-form    meetings-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :project-view {:tt "Current Project"}

                                     :edit {
                                            :tt "Edit Meeting"
                                            :handler (fn [_]
                                                       (rf/dispatch [:common/show-details-edit-item true])
                                                       )
                                            }

                                     :remove {
                                              :tt "Remove Meeting"
                                              :handler (fn [form-data]
                                                         (log/info "Remove record ")
                                                         (rf/dispatch [:crud-ops/remove {:type :meeting
                                                                                         :record form-data}]))}}}]




    (rf/dispatch-sync [:init-form-config form-config])
    (fn [_]
      [c/generic-form])))

(defn meetings-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [meetings-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [meetings-detail-view list-item]
          [c/no-item-found "Meeting Minutes"])

        :else
        [:div "don't know what to do "]))))


;;; Main entry for company master-detail page
(defn meetings-page []
  (let [page-config
        {:form-state    {
                         :form-name         :meetings
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

                                                            :img-icon "fa fa-free-code-camp"
                                                            ;:on-click c/select-item-and-edit-details
                                                            }

                         :master-list-item-std-detail-link #(site/meeting-route {:meeting-id (.toString (:_id %))})
                         :master-list-link                 #(site/meetings-route)
                         :detail-area-main                 meetings-detail-area
                         }}]

    (rf/dispatch-sync [:init-meetings-page page-config])
    (fn []
      [c/master-detail-page])))


;; Initial meetings page state
(rf/reg-event-fx
  :init-meetings-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    {:db (-> db
             (s/assoc-master-config master-config)
             (s/assoc-form-state (merge (s/form-state db) form-state)))

     :load-and-cache [:meetings/list 100]}))

