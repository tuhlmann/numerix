(ns numerix.views.contact
  (:require [reagent.core :as r]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [taoensso.encore :as enc]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.views.base :as base]
            [taoensso.timbre :as log]
            [numerix.history :as hist]
            [re-com.core :refer-macros [handler-fn]]
            [re-frame.db :as db]
            [re-frame.core :as rf]
            [validateur.validation :as v]))

(defn contact-form-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Name" :value (:name @form-data)]
       [f/static-field :label "Email" :value (:email @form-data)]
       [f/pfield :type :textarea :label "Your Message" :path :message :rows 5]])))

; The contact form
(defn contact-form []
  (let [user-data (rf/subscribe [:current-user])
        contact-data {
                      :name (:name @user-data)
                      :email (:email @user-data)
                      :userId (:_id @user-data)
                      :message ""
                      }
        form-config {:title         [:span "Contact Us"]
                     :title-subtext [:span "Let us know what you think"]
                     :card-title    [:span "Contact Us"]
                     :inner-form    contact-form-inner
                     :standalone?   true
                     :form-data     contact-data

                     :validation    (v/validation-set
                                      (v/length-of :message :within (range 3 300) :blank-message "Please provide a message"))

                     :buttons       {
                                     :cancel {
                                              :tt      "Close the dialog"
                                              :handler (handler-fn (rf/dispatch [:go-to-home]))
                                              }
                                     :send   {
                                              :tt      "Send your message"
                                              :handler (fn [form-data]
                                                         (rf/dispatch [:user/send-contact-message form-data]))
                                              }
                                     }
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))


(defn contact-page []
  (rf/dispatch-sync [:init-form-state {}])
  (fn []
    [base/base
     [:div {:key (enc/uuid-str 5)}
      [:div.row
       [:div.col-md-8.offset-md-2.col-12
        [contact-form]]]]]))