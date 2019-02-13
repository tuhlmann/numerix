(ns numerix.views.timeroll
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [reagent.session :as session]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
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
            [numerix.api.crud-ops :as crud-ops]
            [re-frame.db :as db]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [numerix.validation.field-vali :as field-vali]))


(defn timeroll-detail-view-inner []
  (let [form-data (subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Date"          :value (h/format-date "dd.MM.yyyy" (:date @form-data))]
       [f/static-field :label "Hours Spent"   :type :float :value (d/min-to-hstr (:minutes @form-data))]
       [f/static-field :label "Description"   :value (:comment @form-data)]])))

(defn timeroll-detail-edit-inner []
  (let [form-data (subscribe [:form-data])]
    (fn []
      (let [entry-date (:date @form-data (t/today-at-midnight))]
        [:form
         [:div.form-group.row
          [:label.form-control-label.col-3 "Date"]
          [:div.col-9
           [datepicker-dropdown
            :model entry-date
            :format "dd.MM.yyyy"
            :show-today? true
            :on-change (fn [d]
                         (dispatch [:form-data-field-change :date d]))
            ]]]


         ;; instead of :parse-type, add :to-val (fn [v]) and :from-val (fn [v])
         ;; default is :from-val uses parse-by-type, :to-val uses toString
         ;; if not added fns will be generated according to :parse-type for :float and :number types
         [f/pfield :label "Hours Spent" :type :float :to-val d/hstr-to-min :from-val d/min-to-hstr :field-width 2 :path :minutes]
         [f/pfield :label "Description" :path :comment]]))))



(defn timeroll-detail-edit []
  (let [form-state (subscribe [:form-state])
        user (subscribe [:current-user])
        timeroll-data (if (:add-new @form-state) (crud-ops/new-record @user {:date (t/today-at-midnight)}) (:selected-item @form-state {}))
        _ (dispatch [:add-to-form-state {:original-record timeroll-data}])
        ;_ (swap! form-state assoc :original-record @timeroll-data)
        form-config {:mode       :edit
                     :title      [:span "Add Timeroll Entry"]
                     :inner-form timeroll-detail-edit-inner
                     :form-data  timeroll-data
                     :validation (v/validation-set
                                   (v/numericality-of :minutes :gt 0 :lte 1440))

                                 :buttons {
                                           :cancel {
                                                    :tt      "Reset Timeroll Entry"
                                                    :handler (fn [_]
                                                               (dispatch [:reset-form-data-error {}])
                                                               (dispatch [:common/show-details-view-item false]))}

                                           :save   {
                                                    :tt      "Save Timeroll Entry"
                                                    :handler (fn [form-data]
                                                               (dispatch [:crud-ops/save
                                                                          {:type :timeroll
                                                                           :record form-data}])
                                                               (rf/dispatch [:common/add-info-alert "Timeroll Entry Saved"]))
                                                    }

                                           :project-switch {:tt "Switch Project"
                                                            :handler (fn [form-data project]
                                                                       (dispatch [:crud-ops/switch-project
                                                                                  {:type :timeroll
                                                                                   :record form-data
                                                                                   :project project}]))
                                                            }
                                           :remove {
                                                    :tt      "Remove Timeroll Entry"
                                                    :handler (fn [form-data]
                                                               (dispatch [:crud-ops/remove {:type :timeroll
                                                                                            :record form-data}]))
                                                    }
                                           }}]


    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn timeroll-detail-view [item]
  (let [form-config {:mode :view
                     :title         [:span "Timeroll"]
                     :title-subtext [:span "View timeroll"]
                     :card-title    [:span "Timeroll "]
                     :inner-form    timeroll-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :project-view {:tt "Current Project"}

                                     :edit {
                                            :tt "Edit Timeroll Entry"
                                            :handler (fn [_]
                                                       (dispatch [:common/show-details-edit-item true]))}

                                     :remove {
                                              :tt "Remove Timeroll Entry"
                                              :handler (fn [form-data]
                                                         (log/info "Remove record ")
                                                         (dispatch [:crud-ops/remove
                                                                    {:type :timeroll
                                                                     :record form-data}]))}}}]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn timeroll-detail-area []
  (let [form-state (subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [timeroll-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [timeroll-detail-view list-item]
          [c/no-item-found "Timeroll"])

        :else
        [:div "don't know what to do "]))))


;;; Main entry for company master-detail page
(defn timeroll-page []
  (let [page-config
        {
         :form-state {
                      :form-name        :timeroll
                      :edit-item        false
                      :show-details     false
                      :is-loading       true
                      :mouse-inside-detail-area-no-follow false
                      :on-outside-click (fn [form-state]
                                          ;(log/info "on outside click called")
                                          ;(when-not
                                          ;  (or (:edit-item form-state)
                                          ;      (not (:show-details form-state)))
                                          ;  (rf/dispatch [:show-detail-area false]))


                                          (when (and (:show-details form-state)
                                                     (v/valid? (s/validation-errors form-state)))
                                            (let [orig (:original-record form-state)
                                                  form-data (rf/subscribe [:form-data])
                                                  diffed (clojure.data/diff orig @form-data)
                                                  has-changes (or (not (nil? (first diffed)))
                                                                  (not (nil? (second diffed))))]
                                              (when has-changes
                                                  ;(dispatch [:common/detail-only-view false])
                                                  (dispatch [:crud-ops/save
                                                             {:type :timeroll
                                                              :record @form-data}]))

                                              (dispatch [:show-detail-area false]))
                                                ;(dispatch [:common/show-details-view-item false])))

                                            ))}

         :master-config {
                         :full-text-fn                     (fn [i] (str (str/lower-case (d/min-to-hstr (:minutes i ""))) " "
                                                                        (str/lower-case (:comment i ""))))
                         :inner-master-list-item           c/master-list-item-std
                         :inner-master-list-item-desc      {
                                                            :title    (fn [v] [:div
                                                                               [:div.col-12.col-sm-4.col-lg-3
                                                                                (str
                                                                                  (h/format-date "dd.MM.yyyy" (:date v))
                                                                                  " / " (d/min-to-hstr (:minutes v)) "h")]
                                                                               [:div.col-12.col-sm-8.col-lg-9 (:comment v)]])


                                                            ;:description (fn [i](str (:comment i)))
                                                            :img-icon "fa fa-clock-o"
                                                            :on-click (fn [form-state master-config list-item]
                                                                        (rf/dispatch [:select-item-and-edit-details list-item]))}

                         :master-list-item-std-detail-link #(site/timeroll-entry-route {:timerollEntryId (.toString (:_id %))})
                         :master-list-link                 #(site/timeroll-route)
                         :detail-area-main                 timeroll-detail-area}
         }]

    (rf/dispatch-sync [:init-timeroll-page page-config])
    (fn []
      [c/master-detail-page])))

(rf/reg-event-fx
  :init-timeroll-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    {:db (-> db
             (s/assoc-master-config master-config)
             (s/assoc-form-state (merge (s/form-state db) form-state)))

     :load-and-cache [:timeroll/list 14]}))

