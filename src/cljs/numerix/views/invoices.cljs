(ns numerix.views.invoices
  (:require [reagent.core :as r]
            [reagent.ratom :as ratom]
            [reagent.interop :refer-macros [$ $!]]
            [reagent.session :as session]
            [re-frame.db :as db]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.views.base :as base]
            [numerix.api.invoice :as invoice-api]
            [numerix.events.invoice]
            [numerix.events.common]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d :refer [ObjectId]]
            [numerix.api.cache :as cache]
            [numerix.site :as site]
            [numerix.fields.sortable-list :refer [sortable-list]]
            [numerix.formatters.invoice :as invf]
            [cljs-time.core :refer [now days minus day-of-week]]
            [cljs-time.format :refer [formatter formatters parse unparse]]
            [clojure.string :as str]
            [cljs.pprint :as pprint]
            [re-com.core :refer [row-button single-dropdown v-box h-box md-circle-icon-button
                                 datepicker datepicker-dropdown]
             :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [validateur.validation :as v]
            [numerix.validation.field-vali :as field-vali]
            [cljs-time.core :as t]
            [numerix.views.common-controls :as ctrl]
            [numerix.api.crud-ops :as crud-ops]
            [re-frame.core :as rf]))

;(defn prop [k]
;  (get (s/server-config) k))

(defn get-company [object-id]
  (let [companies (cache/cache-contacts)]
    (first (filter #(= (:_id %) object-id) @companies))))

(defn invoice-detail-view-inner []
  (let [form-state (rf/subscribe [:form-state])
        form-data (rf/subscribe [:form-data])
        user (rf/subscribe [:current-user])
        textblocks (rf/subscribe [:textblocks])
        company (get-company (:companyId @form-data))]
    (fn []
      (let [item @form-data]
        [:form
         [:div.row.hide-no-print {:style {:margin-bottom "20px"}}
          [:div.col-12 {:style {:display :flex}}
           [:div {:style {:margin-left :auto}} (:name user) [:br]
            (:contact user) [:br]
            (:street user) [:br]
            (:zip user) " " (:city user) [:br]]]]

         [:div.row.hide-no-print
          [:div.col-6
           [:div.text-small {:style {:border-bottom "1px solid black"
                                     :margin-bottom "10px"}}
            (str (:contact user) " " (:name user) ", " (:street user) ", " (:zip user) " " (:city user))]]]

         [:div.row {:style {:margin-bottom "20px"}}
          [:div.col-sm-5.col-12
           [:span.pull-left (:name company) [:br]
            (:street1 company) [:br]
            (:zip company) " " (:city company) [:br]
            (:country company)]]

          [:div.col-sm-5.offset-sm-2.col-12
           [:div.pull-right
            ;(log/info "Invoice date " (:invoice-date item))
            "Invoice PDF: " (str (get-in item [:invoice-pdf :document-id])) [:br]
            "Invoice Date: " (h/format-date "dd.MM.yyyy" (:invoice-date item)) [:br]
            "Delivery Date: " (h/format-date "dd.MM.yyyy" (:delivery-date item)) [:br]
            "VAT ID: " (:vat-number user) [:br]]]]

         [:div.row.hide-no-print
          [:div.col-12
           [:h2 (str "Invoice " (:invoice-no item))]]]

         (if (:textblockIds item)
           [:div.row.m-b-md.m-t-md
            [:div.col-12
             [:div.border-t.border-b
              (let [blocks (mapv
                             (fn [id] (first (filterv (fn [block] (= (:_id block) id)) @textblocks)))
                             (:textblockIds item))]
                (doall (for [[idx block] (map-indexed vector blocks)]
                         ^{:key (str "textblock-" idx)}
                         [:p.m-b-half.last-item-m-b-0 (:text block)])))]]])

         [:table.table.table-striped.table-sm.invoice-table
          [:thead
           [:tr
            [:th {:style {:width "10%"}} "Qty"]
            [:th {:style {:width "55%"}} "Summary"]
            [:th {:style {:width "10%"}} [:span.pull-right "Amount"]]
            [:th {:style {:width "10%"}} [:span.pull-right "Vat %"]]
            [:th {:style {:width "15%"}} [:span.pull-right (str "Total (" (:currency company) ")")]]]]
          [:tbody
           (for [[idx row] (map-indexed vector (:invoice-items item))]
             ^{:key idx}
             [:tr
              [:td [f/static-field :value (:quantity row)]]
              [:td [f/static-field :value (:summary row)]]
              [:td [:span.pull-right [f/static-field :value (pprint/cl-format nil  "~,2f" (:amount row))]]]
              [:td [:span.pull-right [f/static-field :value (:vat row)]]]
              [:td [:span.pull-right [f/static-field :value (pprint/cl-format nil "~,2f" (invf/calc-net row))]]]])]

          [:tfoot
            [:tr.invoice-table-footer {:style {:page-break-inside "avoid"}}
             [:td {:col-span 4} "Net Total"]
             [:td [:span.pull-right [f/static-field :value (pprint/cl-format nil "~,2f" (invf/calc-net-total (:invoice-items item)))]]]]

            (doall (for [vat-value (distinct (map :vat (:invoice-items item)))]
                     (do
                       ^{:key (str "vat-row-" vat-value)}
                       [:tr
                        [:td {:col-span 2}
                         (if (= vat-value 0)
                           "non-taxable"
                           (str vat-value "% Vat applied to"))]
                        [:td [:span.pull-right [f/static-field :value (pprint/cl-format nil "~,2f" (invf/calc-net-total (:invoice-items item) vat-value))]]]
                        [:td
                         (when-not (= vat-value 0)
                           [:span.pull-right [f/static-field :value (pprint/cl-format nil  "~,2f" (invf/calc-vat-total (:invoice-items item) vat-value))]])]

                        [:td [:span.pull-right
                              [f/static-field
                               :value (pprint/cl-format nil  "~,2f"
                                                        (invf/calc-gross-total (:invoice-items item) vat-value))]]]])))


            [:tr
             [:th {:col-span 4} (str "Amount Payable (" (:currency company) ")")]
             [:th [:span.pull-right
                   [f/static-field
                    :value (pprint/cl-format nil  "~,2f" (invf/calc-gross-total (:invoice-items item)))]]]]]]]))))



(defn count-rows [text]
  (.round js/Math (max 2 (/ (count text) 50))))

(defn init-invoice-row [company]
  {
   :quantity 1
   :vat (or (:default-vat company) 0)})

(defn reaction [value]
  (ratom/make-reaction (fn []
                         (log/info "run reaction " value)
                         value) {:auto-run true}))

(defn invoice-detail-edit-inner []
  (let [form-config (rf/subscribe [:form-config])
        form-data (rf/subscribe [:form-data])
        companies (cache/cache-contacts)
        textblocks (rf/subscribe [:textblocks])
        invoice-rows (rf/subscribe [:form-data-field :invoice-items])
        ;delivery-date-obj (r/atom (h/iso8601->date @delivery-date))
        mouse-over (r/atom -1)
        clicked-row-idx (r/atom -1)]

    (fn []
      (let [current-row-idx @mouse-over
            cur-click-idx @clicked-row-idx
            companyId (:companyId @form-data)
            textblockIds (:textblockIds @form-data)
            invoice-date (:invoice-date @form-data (t/today-at-midnight))
            ;invoice-date-obj (r/atom (h/iso8601->date @invoice-date))
            delivery-date (:delivery-date @form-data (t/today-at-midnight))
            company (first (filter #(= companyId (:_id %)) @companies))
            vali-res (f/validate-form-all @form-config @form-data)]
        [:form
         ;[f/select-field "Company" (r/cursor form-data [:company]) @companies]
         [:div.row
          [:div.col-sm-4.col-12
           [h-box
            :gap "1rem"
            :align :center
            :children [
                       [single-dropdown
                        :choices      companies
                        :id-fn        #(get-in % [:_id])
                        :label-fn     #(:name %)
                        :model        companyId
                        :width        "100%"
                        :filter-box?  true
                        :placeholder  "Select Company"
                        :on-change    (fn [id]
                                        ;(reset! companyId id)
                                        (rf/dispatch [:form-data-field-change :companyId id])
                                        ;(f/validate-form)
                                        (when-not (:invoice-items @form-data)
                                          (when-let [c (first (filter #(= id (:_id %)) @companies))]
                                            ;(swap! form-data assoc :invoice-items [(init-invoice-row c)])
                                            (rf/dispatch [:form-data-field-change :invoice-items [(init-invoice-row c)]])
                                            )))]

                       (when-not (empty? (v/errors :companyId vali-res))
                         [md-circle-icon-button
                          :md-icon-name "zmdi zmdi-alert-circle"
                          :tooltip      "Please select a company"
                          :size         :smaller
                          :class "no-border"
                          :style {:color "red"}
                          :on-click #()])]]]

          [:div.col-sm-8.col-12
           [:div.form-group.pull-right
             [:label.form-control-label "Invoice Date / Delivery Date:"]
             [datepicker-dropdown
              :model invoice-date
              :format "dd.MM.yyyy"
              :show-today? true
              :on-change (fn [d]
                           ;(reset! invoice-date d)
                           (rf/dispatch [:form-data-field-change :invoice-date d]))]
                            ;(reset! invoice-date (h/date->iso8601 d))


             " / "
              [datepicker-dropdown
               :model delivery-date
               :format "dd.MM.yyyy"
               :show-today? true
               :on-change (fn [d]
                            ;(reset! delivery-date d)
                            (rf/dispatch [:form-data-field-change :delivery-date d]))]]]]
                            ;(reset! delivery-date (h/date->iso8601 d))


         [:div.row.m-b-md
          [:div.col-sm-7.offset-sm-5.col-12
           (when (< (count textblockIds)
                    (count @textblocks))
            [:div.row.form-group
             [:div.col-6
              [:label.form-control-label {:style {
                                                  :display :flex
                                                  :justify-content :flex-end
                                                  }} "Text Blocks:"]]
             [:div.col-6
              [h-box
               :gap "1rem"
               :align :center
               :children [
                          [single-dropdown
                           :choices      (vec (filter #(not-any? #{(:_id %)} textblockIds) @textblocks))
                           :id-fn        #(get-in % [:_id])
                           :label-fn     #(:name %)
                           :model        (first textblockIds)
                           :width        "100%"
                           :filter-box?  true
                           :disabled?    (= (count textblockIds) (count @textblocks))
                           :placeholder  "Select Text Block"
                           :on-change    (fn [id]
                                           (when-not (some #{id} textblockIds)
                                             (rf/dispatch [:form-data-field-change :textblockIds (conj textblockIds id)])
                                             ;(swap! textblockIds conj id)
                                             ;(f/validate-form)
                                             ))]]]]])]]

         [:div.row.m-b-md
          [:div.col-sm-12
           (let [blocks (mapv
                          (fn [id] (first (filterv (fn [block] (= (:_id block) id)) @textblocks)))
                          textblockIds)]

             [c/css-transition-group {:transition-name "announce"
                                      :transition-enter-timeout 500
                                      :transition-leave-timeout 300}

              [sortable-list
               :on-drop (fn [from-idx to-idx]
                          (rf/dispatch [:form-data-field-change :textblockIds (h/vec-move textblockIds from-idx to-idx)])
                          ;(swap! textblockIds h/vec-move from-idx to-idx)
                          )
               :children
               (map-indexed (fn [idx block]
                              [:div.reveal-on-focus
                               [:a.pull-left.color-light-control-elem.revealed-on-focus
                                {:title "Remove this text block"
                                 :href "Javascript://"
                                 :on-click (fn []
                                             (log/info "remove item")
                                             (rf/dispatch [:form-data-field-change :textblockIds (h/vec-remove textblockIds idx)])
                                             ;(swap! textblockIds h/vec-remove idx)
                                             )}

                                [:i.fa.fa-times]]
                               [:span {:style {:margin-left "5px" :cursor "move"}}
                                (:text block)]]) blocks)]]
             )]]
         [:div.row
          [:div.col-sm-12

           [:table.table.table-striped.table-hover
            [:thead
             [:tr
              [:th {:style {:width "10%"}} "Qty"]
              [:th {:style {:width "10%"}} "Amount"]
              [:th {:style {:width "50%"}} "Summary"]
              [:th {:style {:width "10%"}}  "Vat"]
              [:th {:style {:width "15%"}} (str "Total (" (:currency company) ")")]
              [:th {:style {:width "5%"}}]]]
            [:tbody
             (doall (for [[idx row] (map-indexed vector @invoice-rows)]
               (do
                 (let [;change-fn (fn [key value]
                       ;            (rf/dispatch [:form-data-field-change :invoice-items (assoc @invoice-rows idx (assoc row key value))]))
                       float-vali (v/compose-sets
                                    field-vali/float-format-validations
                                    (v/validation-set
                                      (v/numericality-of :value)))
                       natural-vali (v/compose-sets
                                      field-vali/natural-format-validations
                                      (v/validation-set
                                        (v/numericality-of :value)))]
                   ^{:key idx}
                   [:tr {:on-mouse-over (handler-fn (reset! mouse-over idx))
                         :on-mouse-out  (handler-fn (reset! mouse-over -1))
                         :on-click      (handler-fn (reset! clicked-row-idx idx))}

                    [:td (if (= idx cur-click-idx)
                           ;[f/field :type :number :value (r/wrap (:quantity row) #(change-fn :quantity %))
                           ; :to-val d/str-to-int :from-val d/int-to-str
                           ; :vali-set natural-vali]
                           [f/pfield :type :number :path [:invoice-items idx :quantity]
                            :to-val d/str-to-int :from-val d/int-to-str
                            :vali-set natural-vali]
                           [f/static-field :value (:quantity row)])]

                    [:td (if (= idx cur-click-idx)
                           [f/pfield :type :float :path [:invoice-items idx :amount]
                            :to-val d/str-to-float :from-val d/float-to-str
                            :vali-set float-vali]
                           [f/static-field :value (:amount row)])]


                    [:td (if (= idx cur-click-idx)
                           [f/pfield :type :textarea :rows (count-rows (:summary row))
                            :path [:invoice-items idx :summary]]
                           [f/static-field :value (:summary row)])]


                    [:td (if (= idx cur-click-idx)
                           [f/pfield :type :number :path [:invoice-items idx :vat]
                            :to-val d/str-to-int :from-val d/int-to-str
                            :vali-set natural-vali]
                           [f/static-field :value (:vat row)])]


                    [:td [f/static-field :value (invf/calc-net row)]]
                    [:td
                     ;[:button.btn.btn-default.btn-transp {:type  "button"
                     ;                                     :style {:display "inline-block"}
                     ;                                     :on-click #(swap! invoice-rows h/vec-remove idx)}
                     ; [:i.fa.fa-minus {:style {:color "red"}}]]
                      [row-button
                       :md-icon-name    "zmdi zmdi-delete"
                       :mouse-over-row? (= current-row-idx idx)
                       :tooltip         "Delete this line"
                       :style {:color "red" :margin-top "10px"}
                       :on-click (handler-fn (rf/dispatch
                                               [:form-data-field-change
                                                :invoice-items (h/vec-remove @invoice-rows idx)]))]]]))))]


            [:tfoot
             [:tr
              [:th {:col-span 4} (str "Invoice Total (" (:currency company) ")")]
              [:th [f/static-field :value (pprint/cl-format nil  "~,2f" (invf/calc-net-total @invoice-rows))]]
              [:th]]]]]]


         [:div.row
          [:div.col-sm-12
           [:button.btn.btn-sm.btn-outline-primary.pull-right
            {:type "button"
             :on-click (handler-fn (rf/dispatch
                                     [:form-data-field-change
                                      :invoice-items
                                      (let [a (conj @invoice-rows (init-invoice-row company))]
                                        (into [] a))]))}

            [:i.fa.fa-plus] " Add"]]]]))))


(defn invoice-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        invoice-data (if (:add-new @form-state)
                       (crud-ops/new-record @user
                                            {:invoice-date (t/today-at-midnight)
                                             :delivery-date (t/today-at-midnight)})
                       (:selected-item @form-state {}))
        form-config {:mode          :edit
                     :title         [:span "Add Invoice"]
                     :title-subtext [:span "Add a new Invoice"]
                     :card-title    [:span "Invoice " (:invoice-no invoice-data)]
                     :inner-form    invoice-detail-edit-inner
                     :form-data     invoice-data
                     :validation    (v/validation-set
                                      (v/presence-of :companyId :message "Please select a company"))

                                    :buttons {
                                              :cancel {
                                                       :tt      "Reset Invoice Data"
                                                       :handler (fn [_]
                                                                  (rf/dispatch [:common/show-details-view-item]))}

                                              :save   {
                                                       :tt      "Save Invoice Data"
                                                       :handler (fn [form-data]
                                                                  (rf/dispatch [:crud-ops/save
                                                                                {:type :invoice
                                                                                 :record form-data}]))}

                                              :project-switch {:tt "Switch Project"
                                                               :handler (fn [form-data project]
                                                                          (rf/dispatch
                                                                            [:crud-ops/switch-project
                                                                             {:type :invoice
                                                                              :record form-data
                                                                              :project project}]))}
                                              }}]




    ;(s/swap-form-config! form-config)
    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn invoice-detail-view [item]
  (let [form-state (rf/subscribe [:form-state])
        form-config {:mode :view
                     :title         (fn [i]
                                      [:span "Invoice " (get-in i [:invoice-no])])
                     :title-subtext (fn [i]
                                      ;(log/info "render invoice subtext " i " , " (:new-pdf-pushed @form-state))
                                      [:span [:span "View Invoice"]
                                       (when (get-in i [:invoice-pdf :document-id])
                                         [:span.pull-right {:class (if (:new-pdf-pushed @form-state)
                                                                     "animated bounceIn long" "")}
                                          (when (:new-pdf-pushed @form-state)
                                            [:span [:i.fa.fa-bullhorn.fa-2x] " "])
                                          [:a {:target "_blank"
                                               :href (str "/api/invoice/download/"
                                                          (get-in i [:invoice-pdf :document-id]) "/"
                                                          (get-in i [:invoice-pdf :document-attachment-id]))}
                                           "Download Invoice PDF " [:i.fa.fa-download]]])])

                     :card-title    (fn [i] [:span "Invoice " (get-in i [:invoice-no])])
                     :inner-form    invoice-detail-view-inner
                     :form-data     item
                     :buttons
                     {
                      :project-view {:tt "Current Project"}

                      :edit {
                             :tt "Edit Invoice"
                             :handler (fn [_]
                                        (rf/dispatch [:common/show-details-edit-item true]))}


                      :preview {
                              :tt "Preview Invoice"
                              :handler (fn [_]
                                         (rf/dispatch [:invoice/show-print-view true]))}

                      :print {
                              :tt "Print Invoice"
                              :handler (fn [form-data]
                                         (rf/dispatch [:invoice/print form-data]))}

                      :remove {
                               :tt "Remove Invoice"
                               :handler (fn [form-data]
                                          (rf/dispatch [:crud-ops/remove
                                                        {:type :invoice
                                                         :record form-data}]))}}}

                      ;:cog-wheel-actions {
                      ;                    :actions (list {
                      ;                                    :title [:span "Print Invoice"]
                      ;                                    ;:handler (handler-fn (emit :admin/send-pwd-reset item))
                      ;                                    })
                      ;                    }
        ]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn [item]
      ;(log/info "render invoice-detail-view " (get-in item [:invoice-pdf]))
      (rf/dispatch [:assoc-form-data item])
      [c/generic-form])))


(defn invoice-print-view []
  (rf/dispatch [:common/enable-full-width-display true])
  (rf/dispatch [:common/show-navigation false])
  (rf/dispatch [:common/detail-only-view true])
  (rf/dispatch [:common/hide-sidebar-if-visible])
  (let [form-config (rf/subscribe [:form-config])]
    (fn [item]
      (rf/dispatch [:assoc-in-form-config :form-data item])
      ;(reset! (:form-data form-config) item)
      [:div.print-view
       [:div.row.hidden-print {:style {:margin-bottom "20px"}}
        [:div.col-12
         [:div.btn-toolbar.pull-left
          [:div.btn-group
           [:a {:href "Javascript://" :on-click #(rf/dispatch [:invoice/show-print-view false])} [:i.fa.fa-chevron-left] "Back"]]]]]
       [:div.row
        [:div.col-12
         [invoice-detail-view-inner]]]])))
      ;; Need to call (js/print) after this view is established


(defn invoice-detail-area []
  (let [form-state (rf/subscribe [:form-state])
        selected-item (rf/subscribe [:form-state-selected-item])]
    (fn []
      (cond
        (:edit-item @form-state)
        [invoice-detail-edit]

        (ctrl/show-print-view? @form-state)
        (if-let [list-item @selected-item]
          [invoice-print-view list-item]
          [c/no-item-found "Invoice"])

        (:show-details @form-state)
        (if-let [list-item @selected-item]
          [invoice-detail-view list-item]
          [c/no-item-found "Invoice"])

        :else
        [:div]))))


;;; Main entry for company master-detail page
(defn invoices-page []
  (let [page-config
        {
         :form-state {
                      :form-name :invoices
                      :edit-item false
                      :show-details false
                      :is-loading true
                      :mouse-inside-detail-area-no-follow false
                      }

         :master-config {
                         :inner-master-list-item c/master-list-item-std
                         :inner-master-list-item-desc {
                                                       :title (fn [i]
                                                                      [:span [:span "Invoice " (get-in i [:invoice-no])]
                                                                       [:span.pull-right (h/format-date "dd.MM.yyyy" (:invoice-date i)) (h/unescape "&nbsp;&nbsp")]])
                                                       :description
                                                                    (fn [v]
                                                                      [:h6 (get-in (get-company (:companyId v)) [:name])])
                                                       :img-icon "fa fa-money fa-2x"}

                         :master-list-item-std-detail-link #(site/invoice-route {:invoiceId (.toString (:_id %))})
                         :master-list-link #(site/invoices-route)
                         :detail-area-main invoice-detail-area}
         }]


    (rf/dispatch-sync [:init-invoices-page page-config])
    (fn []
      [c/master-detail-page])))


(rf/reg-event-fx
  :init-invoices-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    {:db (-> db
             (s/assoc-master-config master-config)
             (s/assoc-form-state (merge (s/form-state db) form-state)))

     :load-and-cache [:invoices/list 1000]}))

