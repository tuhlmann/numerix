(ns numerix.events.invoice
  (:require [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.state :as s]
            [numerix.ws :as socket]
            [numerix.api.invoice :as invoice-api]
            [numerix.views.common-controls :as ctrl]
            [re-frame.core :as rf]
            [numerix.api.crud-ops :as crud-ops]))

(rf/reg-fx
  :invoice-api-fn
  (fn [[fn-id args]]
    (case fn-id

      :show-print-view
      (let [{:keys [show-print-view?]} args]
        (rf/dispatch [:common/show-sidebar-if-visible-previously])
        (rf/dispatch [:common/show-navigation (not show-print-view?)])
        (rf/dispatch [:common/detail-only-view show-print-view?])
        (rf/dispatch [:common/enable-full-width-display show-print-view?])
        (rf/dispatch [:mouse-inside-detail-area show-print-view?])
        (if show-print-view?
          (ctrl/remove-body-background)
          (ctrl/add-body-background)))

      :print
      (let [{:keys [invoice]} args]
        (invoice-api/print-invoice
          invoice
          (fn [response]
            (log/info "print response " (pr-str response))
            (when (socket/response-ok response)
              (rf/dispatch [:crud-ops/save-success {:type :invoice
                                                    :record invoice}])))))

      )))



;;; EVENTS TRIGGERED BY THE VIEW

;(ctrl/show-print-view (s/form-state-cursor) f))

(rf/reg-event-fx
  :invoice/show-print-view
  (fn [cofx [_ b]]
    (let [new-db (s/assoc-in-form-state (:db cofx) :show-print-view b)]
      {
       :db new-db
       :invoice-api-fn [:show-print-view {:show-print-view? b}]})))

(rf/reg-event-fx
  :invoice/print
  (fn [cofx [_ invoice]]
    {
     :db (:db cofx)
     :invoice-api-fn [:print {:invoice invoice}]}))


(rf/reg-event-db
  :invoice/invoice-pdf-updated
  (fn [db [_ invoice]]
    (let [master-data (crud-ops/add-or-replace-record-in (s/master-data db) invoice)
          form-state (assoc (s/form-state db) :selected-item invoice)
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data)
                     (s/open-document-in (str "/api/invoice/download/"
                                              (get-in invoice [:invoice-pdf :document-id])))
                     (s/add-form-state-auto-flag [:new-pdf-pushed] true 10)
                     (ctrl/show-details-view-item true)
                     )]

      new-db)))

