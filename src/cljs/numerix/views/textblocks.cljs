(ns numerix.views.textblocks
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.events.common]
            [numerix.site :as site]
            [cljs-time.core :refer [now days minus day-of-week]]
            [cljs-time.format :refer [formatter formatters parse unparse]]
            [clojure.string :as str]
            [re-com.core :refer [row-button single-dropdown v-box h-box md-circle-icon-button
                                 datepicker datepicker-dropdown]
             :refer-macros [handler-fn]]
            [validateur.validation :as v]
            [numerix.api.crud-ops :as crud-ops]
            [re-frame.db :as db]
            [re-frame.core :as rf :refer [dispatch]]))


(defn textblock-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Title" :value (:name @form-data)]
       [f/static-field :label "Text"  :value (:text @form-data)]])))

(defn textblock-detail-edit-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/pfield :label "Title" :path :name]
       [f/pfield :label "Text" :path :text :type :textarea :rows 5]
       ])))


(defn textblock-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        textblock-data (if (:add-new @form-state) (crud-ops/new-record @user) (:selected-item @form-state {}))
        form-config {:mode          :edit
                     :title         [:span "Add Textblock"]
                     :title-subtext [:span "Add a new Textblock"]
                     :card-title    [:span "Textblock " (:name textblock-data)]
                     :inner-form    textblock-detail-edit-inner
                     :form-data     textblock-data
                     :validation    (v/validation-set
                                      (v/length-of :name :within (range 3 200) :blank-message "Please provide a title"))

                                    :buttons {
                                              :cancel {
                                                       :tt      "Reset Textblock Data"
                                                       :handler (fn [_]
                                                                  (dispatch [:common/show-details-view-item]))
                                                       }
                                              :save   {
                                                       :tt      "Save Textblock Data"
                                                       :handler (fn [form-data]
                                                                  (dispatch [:crud-ops/save
                                                                             {:type :textblock
                                                                              :record form-data}]))
                                                       }
                                              :project-switch {:tt "Switch Project"
                                                               :handler (fn [form-data project]
                                                                          (dispatch [:crud-ops/switch-project
                                                                                {:type :textblock
                                                                                 :record form-data
                                                                                 :project project}]))
                                                               }
                                              }
                     }]

    ;(s/swap-form-config! form-config)
    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn textblock-detail-view [item]
  (let [form-config {:mode :view
                     :title         [:span "Textblock" ]
                     :title-subtext [:span "View textblock"]
                     :card-title    [:span "Textblock "]
                     :inner-form    textblock-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :project-view {:tt "Current Project"}

                                     :edit {
                                            :tt "Edit Textblock"
                                            :handler (fn [_]
                                                       (dispatch [:common/show-details-edit-item true]))
                                            }
                                     :remove {
                                              :tt "Remove Textblock"
                                              :handler (fn [form-data]
                                                         (dispatch [:crud-ops/remove
                                                                    {:type :textblock
                                                                     :record form-data}]))
                                              }
                                     }
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn textblock-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [textblock-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [textblock-detail-view list-item]
          [c/no-item-found "Text Block"])

        :else
        [:div]))))


;;; Main entry for company master-detail page
(defn textblocks-page []
  (let [textblocks (rf/subscribe [:textblocks])
        page-config
        {
         :form-state {
                      :form-name :textblocks
                      :permission-name :invoices
                      :edit-item false
                      :show-details false
                      :mouse-inside-detail-area-no-follow false
                      }

        :master-config {
                        :master-data @textblocks
                        :inner-master-list-item c/master-list-item-std
                        :inner-master-list-item-desc {
                                                      :title (fn [v] [:h6 (str "Text Block " (:name v))])
                                                      :description [:span ""]
                                                      ;:img-url "/img/portfolio/submarine.png"
                                                      :img-icon "fa fa-comments-o fa-2x"
                                                      }
                        :master-list-item-std-detail-link #(site/textblock-route {:textblockId (.toString (:_id %))})
                        :master-list-link #(site/textblocks-route)
                        :detail-area-main textblock-detail-area
                        }
         }]

    (rf/dispatch-sync [:init-textblocks-page page-config])
    (fn []
      [c/master-detail-page])))


;; Initial textblocks page state
(rf/reg-event-fx
  :init-textblocks-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    (let [new-db
          (-> db
              (s/assoc-master-config master-config)
              (s/assoc-form-state (merge (s/form-state db) form-state))
              (s/assoc-in-form-state :selected-item (c/find-selected-item (:master-data master-config))))

          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]

      {:db new-db})))

