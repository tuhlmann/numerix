(ns numerix.views.admin.users-page
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [reagent.session :as session]
            [re-frame.core :refer [dispatch]]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.views.base :as base]
            [numerix.api.cache :as cache]
            [numerix.events.common :as common-events]
            [numerix.events.admin :as admin-events]
            [numerix.subs.common :as common-subs]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d]
            [numerix.site :as site]
            [numerix.validation.auth-vali :as auth-vali]
            [cuerdas.core :as cue]
            [cljs-time.core :refer [now days minus day-of-week]]
            [cljs-time.format :refer [formatter formatters parse unparse]]
            [clojure.string :as str]
            [re-com.core :refer [row-button single-dropdown v-box h-box md-circle-icon-button
                                 datepicker datepicker-dropdown]
             :refer-macros [handler-fn]]
            [validateur.validation :as v]
            [numerix.api.admin :as admin-api]
            [numerix.events.cache :as cache-events]
            [re-frame.core :as rf]))


(defn admin-users-detail-view-inner []
  (let [user (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Email"               :value (:email @user)]
       [f/static-field :label "Name"                :value (:name @user)]
       [f/static-field :label "Company"             :value (:company @user)]
       [f/static-field :label "Street"              :value (:street @user)]
       [f/static-fields :label "Zip / City"
        :fields [{:value (:zip @user) :field-width 3}
                 {:value (:city @user) :field-width 6}]]
       [f/static-field :label "Country"             :value (:country @user)]
       [f/static-field :label "Vat-Number"          :value (:vat-number @user)]
       [f/static-field :label "Invoice No Pattern"  :value (:invoice-no-pattern @user)]
       [f/static-field :label "Description"         :value (:bio @user)]
       [f/static-field :label "Login Token"         :value (:login-token @user)]
       [f/static-field :label "Token Expires"
        :value (if (:login-token-expires @user) (h/format-date "dd.MM.yyyy HH:mm" (:login-token-expires @user)) "")]
       [f/static-field :label "Roles"               :value (cue/join ", " (:roles @user))]
       [f/static-field :label "Validated"           :value (:validated @user) :type :checkbox]
       ])))

(defn admin-users-detail-edit-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/pfield :label "Email"    :path :email]
       [f/pfield :label "Name"     :path :name]
       [f/pfield :label "Company"  :path :contact]
       [f/pfield :label "Street"   :path :street]

       [f/pfields :label "Zip / City"
        :fields [
                 {:path :zip :field-width 3}
                 {:path :city :field-width 6}]]

       [f/pfield :label "Country"   :path :country]
       [f/pfield :label "VAT-Number" :path :vat-number]
       [f/pfield :label "Invoice No Pattern" :path :invoice-no-pattern
        :help [:div "This pattern defines what your invoice number looks like. The following placeholders are allowed:"
               [:ul
                [:li "counter:<default> - Inserts a sequential counter, unique per user.
              You can set the last used value of the counter. If the counter is not found this one
              is used or 0 if not defined."]]
               "Example: INV-2016-{counter:15} - The first invoice generated would be 16."
               ]]
       [f/pfield :type :textarea :label "Description" :path :bio :rows 3]
       [f/static-field :label "Login Token"         :value (:login-token @form-data)]
       [f/static-field :label "Token Expires"
        :value (if (:login-token-expires @form-data) (h/format-date "dd.MM.yyyy HH:mm" (:login-token-expires @form-data)) "")]
       [f/pfield       :label "Roles"                :path :roles]
       [f/pfield       :label "Validated"            :path :validated :type :checkbox]
       ])))


(defn admin-users-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        ;{:keys [master-data]} (s/master-config-map)
        user-data (if (:add-new @form-state) (admin-api/new-user-rec) (:selected-item @form-state {}))
        ;user-data (r/atom init-map)
        form-config {:mode          :edit
                     :title         [:span "Add User"]
                     :title-subtext [:span "Add a new User"]
                     :card-title    [:span "User " (:name user-data)]
                     :inner-form    admin-users-detail-edit-inner
                     :form-data     user-data
                     :validation    (v/validation-set
                                      (v/length-of :name :within (range 3 200) :blank-message "Please provide a name")
                                      (v/validate-with-predicate :email (fn [{:keys [email]}] (auth-vali/is-optional-email? email)) :message "Please enter a valid email address")
                                      ;(v/length-of :currency :within (range 1 4) :blank-message "Add Currency")
                                      ;(v/format-of :default-vat :format pos-natural-pattern :message "Needs positive numeric value")
                                      ;(v/numericality-of :default-vat)
                                      )

                     :buttons {
                               :cancel {
                                        :tt      "Cancel changes"
                                        :handler (fn [_]
                                                   (dispatch [:common/show-details-view-item]))
                                        }
                               :save   {
                                        :tt      "Save User"
                                        :handler (fn [form-data]
                                                   (dispatch [:admin/save-user form-data]))
                                        }
                               }
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn admin-users-detail-view [item]
  (let [form-config {:mode :view
                     :title         [:span "User" ]
                     :title-subtext [:span "View User"]
                     :card-title    [:span "User "]
                     :inner-form    admin-users-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :edit {
                                            :tt "Edit User"
                                            :handler (fn [form-data]
                                                       (dispatch [:common/show-details-edit-item true]))
                                            }
                                     :remove {
                                              :tt "Remove User"
                                              :handler (fn [form-data]
                                                         (log/info "Remove record ")
                                                         (dispatch [:admin/remove-user form-data]))
                                              }
                                     :cog-wheel-actions {
                                                         :actions (list {
                                                                         :title [:span "Send Password Reset"]
                                                                         :handler (handler-fn (dispatch [:admin/send-pwd-reset item]))
                                                                         }
                                                                        {:title   [:span "Clear Login Token"]
                                                                         :handler (handler-fn (dispatch [:admin/clear-login-token item]))
                                                                         }
                                                                        {:title [:span "Login as User"]
                                                                         :href (str "/login-as/" (.toString (:_id item)))
                                                                         })
                                                         }

                                     }
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

;;; Dispatcher between list / detail view / detail edit
(defn admin-users-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [admin-users-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [admin-users-detail-view list-item]
          [c/no-item-found "Users"])

        :else
        [:div]))))

;;; Main entry for company master-detail page
(defn admin-users-page []
  (let [page-config
        {:form-state {:form-name    :admin-users
                      :edit-item    false
                      :show-details false
                      :is-loading   true}

         :master-config {
                         :full-text-fn                     (fn [i] (str (str/lower-case (:email i)) " "
                                                                        (str/lower-case (:name i "")) " "
                                                                        (str/lower-case (:contact i "")) " "
                                                                        (str/lower-case (:street i "")) " "
                                                                        (str/lower-case (:zip i "")) " "
                                                                        (str/lower-case (:city i ""))))
                         :inner-master-list-item           c/master-list-item-std
                         :inner-master-list-item-desc {
                                                       :title (fn [v] [:div
                                                                       [:div.col-12
                                                                        [:h6 (:name v) " / " (:email v)]]
                                                                       [:div.col-12 (:contact v) ", " (:street v) ", "
                                                                        (:zip v) " " (:city v)]])
                                                       ;:description (fn [i](str (:comment i)))
                                                       :img-icon "fa fa-user fa-2x"
                                                       }
                         :master-list-item-std-detail-link #(site/admin-user-route {:userId (.toString (:_id %))})
                         :master-list-link                 #(site/admin-users-route)
                         :detail-area-main                 admin-users-detail-area
                       }}]

    (rf/dispatch-sync [:init-admin-users-page page-config])

    (fn []
      [c/master-detail-page])))
