(ns numerix.views.memberships
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
            [re-frame.core :as rf :refer [dispatch]]
            [numerix.validation.auth-vali :as auth-vali]
            [numerix.api.tag :as tag-api]
            [numerix.lib.datatypes :as d]
            [numerix.lib.helpers :as h]))


(defn membership-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Email Address" :value (:email @form-data)]
       [f/static-field :label "Joined" :value (h/format-date "dd.MM.yyyy" (d/oid-time (:_id @form-data)))]
       [f/static-field :label "Status" :value (:connection-status @form-data) ]
       [f/static-field :label "Roles" :type :tag :tag-icon-class "" :value (:roles @form-data)]
       [f/static-field :label "Invitation Message"  :value (:message @form-data)]])))

(defn membership-detail-edit-inner []
  (let [form-data (rf/subscribe [:form-data])
        roles (tag-api/list-user-roles)]
    (fn []
      (if (:_id @form-data)
        [:form
         [f/static-field :label "Email Address" :value (:email @form-data)]
         [f/static-field :label "Joined" :value (h/format-date "dd.MM.yyyy" (d/oid-time (:_id @form-data)))]
         [f/static-field :label "Status" :value (:connection-status @form-data) ]
         [f/pfield :label "Roles" :path :roles :type :tag
          :categories roles :add-new false :hide-single-category false :placeholder "Select some roles"]
         [f/pfield :label "Invitation Message" :path :message :type :textarea :rows 5
          :help "Optionally provide a message"]
         ]

        [:form
         [f/pfield :label "Email Addresses" :path :emails
          :help "Enter one or more email addresses separated by space"]
         [f/pfield :label "Roles" :path :roles :type :tag
          :categories roles :add-new false :hide-single-category false :placeholder "Select some roles"]
         [f/pfield :label "Invitation Message" :path :message :type :textarea :rows 5
          :help "Optionally provide a message"]
         ]

      ))))


(defn membership-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        membership-data (if (:add-new @form-state) (crud-ops/new-record @user) (:selected-item @form-state {}))
        form-config {:mode          :edit
                     :title         [:span "Add Project Membership"]
                     :title-subtext [:span "Add a new member to the project"]
                     :card-title    [:span "Membership " (:email membership-data)]
                     :inner-form    membership-detail-edit-inner
                     :form-data     membership-data
                     :validate-fn   (fn [form-data]
                                      (if (:_id form-data)
                                        auth-vali/edit-membership-validations
                                        auth-vali/invite-members-validations))

                     :buttons       (fn [form-data]
                                      (let [common
                                            {:cancel {:tt      "Cancel Invitation"
                                                      :handler (fn [_]
                                                                 (dispatch [:common/show-details-view-item]))
                                                      }}]
                                        (if (nil? (:_id form-data))
                                          (merge
                                            common
                                            {:invite {:tt      "Invite new project members"
                                                      :handler (fn [form-data]
                                                                 (dispatch [:memberhip/invite form-data]))}})
                                          (merge
                                            common
                                            {:save {:tt      "Save Project Membership"
                                                    :handler (fn [form-data]
                                                               (dispatch [:crud-ops/save
                                                                          {:type   :membership
                                                                           :record form-data}]))}}))))
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn membership-detail-view [item]
  (let [form-config {:mode          :view
                     :title         [:span "Project Membership" ]
                     :title-subtext [:span "View membership"]
                     :card-title    [:span "Project Membership "]
                     :inner-form    membership-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :project-view {:tt "Current Project"}

                                     :edit {
                                            :tt "Edit Membership"
                                            :handler (fn [_]
                                                       (dispatch [:common/show-details-edit-item true]))
                                            }
                                     :remove {
                                              :tt "Remove Membership"
                                              :handler (fn [form-data]
                                                         (dispatch [:crud-ops/remove
                                                                    {:type   :membership
                                                                     :record form-data}]))
                                              }
                                     }
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn membership-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [membership-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [membership-detail-view list-item]
          [c/no-item-found "Project Membership"])

        :else
        [:div]))))


;;; Main entry for company master-detail page
(defn memberships-page []
  (let [memberships (rf/subscribe [:memberships])
        page-config
        {
         :form-state    {
                         :form-name    :memberships
                         :edit-item    false
                         :show-details false
                         :mouse-inside-detail-area-no-follow false
                         }

         :master-config {
                         :master-data                      @memberships
                         :master-area-control-create-lbl   [:span [:i.fa.fa-plus] " Invite"]
                         :inner-master-list-item           c/master-list-item-std
                         :inner-master-list-item-desc      {
                                                            :title       (fn [v]
                                                                           [:h6 (str (:email v), ", joined "
                                                                                     (h/format-date "dd.MM.yyyy"
                                                                                                    (d/oid-time (:_id v))))])
                                                            :description [:span ""]
                                                            ;:img-url "/img/portfolio/submarine.png"
                                                            :img-icon    "fa fa-user fa-2x"
                                                            }
                         :master-list-item-std-detail-link #(site/membership-route {:memberId (.toString (:_id %))})
                         :master-list-link                 #(site/memberships-route)
                         :detail-area-main                 membership-detail-area
                         }
         }]

    (rf/dispatch-sync [:init-memberships-page page-config])
    (fn []
      [c/master-detail-page])))


;; Initial textblocks page state
(rf/reg-event-fx
  :init-memberships-page
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

