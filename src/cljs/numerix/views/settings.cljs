(ns numerix.views.settings
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [numerix.events.user :as user-events]
            [taoensso.encore :as enc]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.views.base :as base]
            [numerix.history :as hist]
            [validateur.validation :as v]
            [taoensso.timbre :as log]
            [numerix.fields.file-upload :refer [file-upload-field]]
            [numerix.fields.image-crop :refer [profile-image-crop-field]]
            [numerix.validation.auth-vali :as auth-vali]
            [re-frame.db :as db]
            [re-frame.core :as rf ]))

(defn settings-form-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:div
      [:form
       [profile-image-crop-field]
       [f/static-field :label "Email" :value (:email @form-data)]
       [f/pfield :label "Name"     :path :name]
       [f/pfield :label "Company"  :path :company]
       [f/pfield :label "Street"   :path :street]

       [f/pfield :label "Zip"   :path :zip]
       [f/pfield :label "City"   :path :city]

       ;[f/fields :label "Zip / City" :fields [
       ;                   {:path :zip :field-width 3}
       ;                   {:path :city :field-width 6}
       ;                   ]]

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
       ;[f/field :type :textarea :label "Description" :path :bio :rows 3]

       ]
       ;[file-upload-field :multiple true]
       ]
      )))

(defn settings-form []
  (let [user-data (rf/subscribe [:current-user])
        form-config {:title [:span "User Settings"]
                     :title-subtext [:span "Change your account settings"]
                     :card-title [:span "Your Account Settings"]
                     :inner-form settings-form-inner
                     :standalone? true
                     :form-data @user-data

                     :validation (v/validation-set
                                   (v/length-of :name :within (range 3 200) :blank-message "Please provide a name")
                                   (v/length-of :company :within (range 3 200) :blank-message "Please provide a company name")
                                   (v/length-of :street :within (range 3 200) :blank-message "Please provide a street name")
                                   (v/length-of :zip :within (range 3 200) :blank-message "Please provide a zip name")
                                   (v/length-of :city :within (range 3 200) :blank-message "Please provide a city name")
                                   (v/length-of :invoice-no-pattern :within (range 3 50) :blank-message "Please provide a number pattern")
                                   (v/format-of :invoice-no-pattern :format f/counter-pattern :message "Please add {counter} to the pattern"))

                     :buttons {
                               :cancel {
                                        :tt "Reset User Data"
                                        :handler #(rf/dispatch [:user/reset-user %])
                                        }
                               :save {
                                      :tt "Save User Data"
                                      :handler #(rf/dispatch [:user/save-user %])
                                     }
                               }
                     }]

    ;(s/swap-form-config! form-config)
    (rf/dispatch-sync [:init-form-config form-config])

    (fn []
      [c/generic-form])))


(defn settings-page []
  (rf/dispatch-sync [:init-form-state {}])

  (fn []
    [base/base
     [:div {:key (enc/uuid-str 5)}
      [:div.row
       [:div.col-md-8.offset-md-2.col-12
        [settings-form]
        ]]]]))

;;; PASSWORD PAGE


(defn password-form-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/pfield :label "Current Password" :type :password :path :current]
       [f/pfield :label "New Password" :type :password :path :password]
       [f/pfield :label "Confirm New Password" :type :password :path  :confirm]
      ])))

(defn password-form []
  (let [password-data {:current "" :password "" :confirm ""}
        form-config {:title         [:span "Password Settings"]
                     :title-subtext [:span "Change your password"]
                     :card-title    [:span "Your Password Settings"]
                     :inner-form    password-form-inner
                     :standalone?   true
                     :form-data     password-data

                     ;instead of just the validate set, you can also use :validate-fn
                     :validation    (v/compose-sets
                                      (v/validation-set
                                        (v/presence-of :current :message "Give the current password"))
                                      auth-vali/pwd-validations
                                      auth-vali/change-pwd-validations
                                      auth-vali/confirm-pwd-validations
                                      )

                     :buttons       {
                                     :cancel {
                                              :tt      "Reset User Data"
                                              :handler #(hist/go-to-home)
                                              }
                                     :save   {
                                              :tt      "Set new Password"
                                              :handler #(rf/dispatch [:user/change-user-password %])
                                              }
                                     }
                     }]

    ;(s/swap-form-config! form-config)
    (rf/dispatch-sync [:init-form-config form-config])

    (fn []
      [c/generic-form])))


(defn password-page []
  (rf/dispatch-sync [:init-form-state {}])

  (fn []
    [base/base
     [:div {:key (enc/uuid-str 5)}
      [:div.row
       [:div.col-md-8.offset-md-2.col-12
        [password-form]
        ]]]]))


