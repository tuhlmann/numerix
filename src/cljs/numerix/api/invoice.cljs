(ns numerix.api.invoice
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [numerix.api.crud-ops :as crud-ops]
            [numerix.views.common :as c]
            [numerix.lib.messages :refer [push-msg-handler]]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d]
            [numerix.views.common-controls :as ctrl]
            [re-frame.core :as rf]))

(defn list-invoices [success-fn]
  (socket/send! [:invoice/list] 5000
                (fn [response]
                  ;(log/info "list companies result " (pr-str response))
                  (success-fn (last response)))))

(defn print-invoice [invoice success-fn]
  (socket/send! [:invoice/print invoice] 5000
                (fn [response]
                  ;(log/info "api print-invoice response is " (pr-str response))
                  (if (socket/response-ok (last response))
                    (do
                      (success-fn (last response)))))))

;; Push answer from server
(defmethod push-msg-handler :invoice/invoice-pdf-updated
  [[_ invoice]]
  (log/info ":invoice/invoice-pdf-updated " invoice)
  (rf/dispatch [:invoice/invoice-pdf-updated invoice]))
