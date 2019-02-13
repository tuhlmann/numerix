(ns numerix.views.contacts
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [re-frame.db :as db]
            [numerix.state :as s]
            [taoensso.encore :as enc]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.views.base :as base]
            [numerix.api.contact :as contact-api]
            [numerix.lib.helpers :as h]
            [numerix.site :as site]
            [clojure.string :as str]
            [numerix.api.cache :as cache]
            [numerix.lib.datatypes :as d]
            [validateur.validation :as v]
            [numerix.lib.gravatar :as grv]
            [numerix.views.common-controls :as ctrl]
            [numerix.validation.auth-vali :as auth-vali]
            [numerix.api.crud-ops :as crud-ops]
            [re-frame.core :as rf]
            [numerix.lib.roles :as roledef]
            [numerix.validation.field-vali :as field-vali]))


(defn contacts-detail-edit-inner []
  (fn []
    [:form
     [f/pfield  :label "Company Name" :path :company-name]
     [f/pfield  :label "Name"  :path :name]
     [f/pfield  :label "Email" :path :email]
     [f/pfield  :label "Address 1" :path :street1]
     [f/pfield  :label "Address 2" :path :street2]

     [f/pfields :label "Zip / City"
      :fields [{:path :zip :field-width 4}
               {:path :city :field-width 5}]]

     [f/pfields :label "Country / VAT-Number"
      :fields [{:path :country :field-width 5}
               {:path :vat-number :field-width 4}]]

     ;[f/pfield  :label "VAT-Number" :path :vat-number :field-width 3]

     [f/pfields :label "Currency / Default VAT"
      :fields [
               {:path :currency :field-width 2}
               {:path     :default-vat :field-width 2 :type :number
                ;:vali-set (v/validation-set
                ;            (v/format-of :shadow-value :format field-vali/pos-natural-pattern :message "Needs positive numeric value")
                ;            (v/numericality-of :value))
                }]]

        ]))


(defn contacts-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        contact (if (:add-new @form-state) (crud-ops/new-record @user) (:selected-item @form-state {}))
        form-config {:mode          :edit
                     :title         (fn [v]
                                      (if (nil? (:_id v))
                                        [:span "Add Contact"]
                                        [:span "Edit Contact"]))
                     :title-subtext (fn [v]
                                      (if (nil? (:_id v))
                                        [:span "Add a new Contact"]
                                        [:span "Edit this Contact"]))
                     :card-title    [:span "Contact"]
                     :inner-form    contacts-detail-edit-inner
                     :form-data     contact
                     :validation    (v/validation-set
                                      (v/length-of :name :within (range 3 200) :blank-message "Please provide a name")
                                      (v/length-of :city :within (range 3 200) :blank-message "Please provide a city")
                                      (v/validate-with-predicate :email (fn [{:keys [email]}] (auth-vali/is-optional-email? email)) :message "Please enter a valid email address")
                                      ;(v/length-of :currency :within (range 1 4) :blank-message "Add Currency")
                                      ;(v/format-of :default-vat :format pos-natural-pattern :message "Needs positive numeric value")
                                      ;(v/numericality-of :default-vat)
                                      )

                     :buttons       {
                                     :project-switch {:tt "Switch Project"
                                                      :handler (fn [form-data project]
                                                                 (rf/dispatch [:crud-ops/switch-project
                                                                               {:type :contact
                                                                                :record form-data
                                                                                :project project}]))}

                                     :cancel         {
                                                      :tt      "Reset Address Data"
                                                      :handler (fn [_]
                                                                 (rf/dispatch [:common/show-details-view-item false]))}

                                     :save           {
                                                      :tt      "Save Address Data"
                                                      :handler (fn [form-data]
                                                                 (rf/dispatch [:crud-ops/save {:type :contact
                                                                                               :record form-data}]))}
                                     }}]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn contacts-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Company Name"  :value (:company-name @form-data) :hide-empty true]
       [f/static-field :label "Name"          :value (:name @form-data) :hide-empty true]
       [f/static-field :label "Email"         :value (:email @form-data) :hide-empty true]
       [f/static-field :label "Address 1"     :value (:street1 @form-data) :hide-empty true]
       [f/static-field :label "Address 2"     :value (:street2 @form-data) :hide-empty true]
       [f/static-fields :label "Zip / City"
        :fields [{:value (:zip @form-data) :field-width 3 :hide-empty true}
                 {:value (:city @form-data) :field-width 6 :hide-empty true}]]
       [f/static-fields :label "Country / VAT-Number"
        :fields [{:value (:country @form-data) :field-width 5 :hide-empty true}
                 {:value (:vat-number @form-data) :field-width 4 :hide-empty true}]]
       ;[f/static-field :label "VAT-Number"    :value (:vat-number @form-data) :hide-empty true]
       [f/static-fields :label "Currency / Default Vat"
        :fields [{:value (:currency @form-data) :field-width 1 :hide-empty true}
                 {:value (:default-vat @form-data) :field-width 1 :hide-empty true}]]])))

(defn contacts-detail-view [item]
  (let [form-config {:mode          :view
                     :title         (fn [v]
                                      (if (not-empty (:company-name v))
                                        [:span "Company"]
                                        [:span "Person"]))
                     :title-subtext (fn [v]
                                      (if (not-empty (:company-name v))
                                        [:span "View Company"]
                                        [:span "View Person"]))
                     :card-title    (fn [v]
                                      (if (not-empty (:company-name v))
                                        [:span "Company"]
                                        [:span "Person"]))
                     :inner-form    contacts-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :edit         {
                                                    :tt      "Edit Company"
                                                    :handler (fn [_]
                                                               (rf/dispatch [:common/show-details-edit-item true]))}

                                     :project-view {:tt "Current Project"}

                                     :remove       {
                                                    :tt      "Remove Company"
                                                    :handler (fn [form-data]
                                                               (log/info "Remove record")
                                                               (rf/dispatch [:crud-ops/remove {:type :contact
                                                                                       :record form-data}]))}
                                     }}]



    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))


(defn contacts-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [contacts-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [contacts-detail-view list-item]
          [c/no-item-found "Address"])

        :else
        [:div]))))


;;; Main entry for company master-detail page
(defn contacts-page []
  (let [page-config {
                     :form-state    {
                                     :form-name    :contacts
                                     :edit-item    false
                                     :show-details false
                                     :is-loading   true
                                     :mouse-inside-detail-area-no-follow false
                                     }

                     :master-config {
                                     :full-text-fn                     (fn [i] (str (str/lower-case (:company-name i "")) " "
                                                                                    (str/lower-case (:name i "")) " "
                                                                                    (str/lower-case (:email i "")) " "))
                                     :inner-master-list-item           c/master-list-item-std
                                     :inner-master-list-item-desc      {
                                                                        :title       (fn [v] [:h6
                                                                                              (if (not-empty (:company-name v))
                                                                                                [:span (:company-name v) " / " (:name v)]
                                                                                                [:span (:name v)])
                                                                                              ])
                                                                        :description (fn [i] (str (:zip i) " " (:city i) " " (:country i)))
                                                                        ;:img-url (fn [v]
                                                                        ;           (grv/image-url (:email v) grv/default-size grv/default-rating "mm"))
                                                                        :img-icon    (fn [v]
                                                                                       (if (not-empty (:company-name v))
                                                                                         "fa fa-building-o fa-2x"
                                                                                         "fa fa-user fa-2x"))
                                                                        }

                                     :master-list-item-std-detail-link #(site/address-route {:address-id (.toString (:_id %))})
                                     :master-list-link                 #(site/addressbook-route)
                                     :detail-area-main                 contacts-detail-area}
                     }]


    (rf/dispatch-sync [:init-contacts-page page-config])
    (fn []
      [c/master-detail-page])))


(rf/reg-event-fx
  :init-contacts-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    {:db             (-> db
                         (s/assoc-master-config master-config)
                         (s/assoc-form-state (merge (s/form-state db) form-state)))

     :load-and-cache [:contacts/list 1000]}))

