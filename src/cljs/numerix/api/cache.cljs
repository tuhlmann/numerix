(ns numerix.api.cache
  (:require [reagent.core :as r]
            [re-frame.db :as db]
            [numerix.state :as s]
            [numerix.views.common :as c]
            [numerix.api.contact :as company-api]
            [numerix.api.invoice :as invoice-api]
            [numerix.api.timeroll :as timeroll-api]
            [numerix.api.admin :as admin-api]
            [numerix.api.meeting :as meeting-api]
            [numerix.api.document :as document-api]
            [numerix.api.knowledgebase :as knowledgebase-api]
            [numerix.api.calendar :as calendar-api]
            [taoensso.timbre :as log]
            [numerix.api.chat :as chat-api]))

#_(defn cursor-from-cache [path retrieve-fn]
   (let [data (r/cursor db/app-db path)]

     (if (empty? @data)
       (invoice-api/list-invoices
         (fn [result]
          ;(enc/logf "Received %s" result)
           (reset! data (get-in result [:data :invoices]))
           (swap! form-state #(-> %
                                  (assoc :selected-item (c/find-selected-item @data))
                                  (assoc :show-details true)))))
       (do
         (log/info "from cache")
         (swap! form-state #(-> %
                                (assoc :selected-item (c/find-selected-item @data))
                                (assoc :show-details true)))))))




(defn cursor-from-cache [& {:keys [path contact-server-fn server-result-path result-strategy client-receive-fn]
                            :or { result-strategy :replace}}]
  (let [data (r/cursor db/app-db path)
        success-fn (fn [result]
                     ;(log/info "cursor-from-cache success " server-result-path (pr-str result))
                     (condp = result-strategy
                       :append
                         (swap! data #(apply vector (concat % (get-in result server-result-path))))
                       (reset! data (get-in result server-result-path)))
                     (when (fn? client-receive-fn)
                           (client-receive-fn data)))]

    (if (empty? @data)
      (do
        ;(log/info "contact server for " (pr-str path))
        (contact-server-fn success-fn))

      (do
        ;(log/info "from cache for " (pr-str path))
        (when (fn? client-receive-fn)
              (client-receive-fn data))))

    data))

(defn cache-invoices [ & [receive-fn]]
  (cursor-from-cache :path [:invoices]
                     :contact-server-fn invoice-api/list-invoices
                     :server-result-path [:data :result]
                     :client-receive-fn receive-fn))

(defn cache-contacts [ & [receive-fn]]
  (cursor-from-cache :path [:contacts]
                     :contact-server-fn company-api/list-contacts
                     :server-result-path [:data :result]
                     :client-receive-fn receive-fn))

(defn cache-timeroll [days-past & [receive-fn]]
  (cursor-from-cache :path [:timeroll]
                     :contact-server-fn (partial timeroll-api/list-timeroll-entries days-past)
                     :server-result-path [:data :result]
                     :result-strategy :append
                     :client-receive-fn receive-fn))


(defn cache-chat-rooms [max & [receive-fn]]
  (cursor-from-cache :path [:chat-rooms]
                     :contact-server-fn (partial chat-api/list-chat-rooms max)
                     :server-result-path [:data :result]
                     :result-strategy :append
                     :client-receive-fn receive-fn))

(defn cache-meetings [max & [receive-fn]]
  (cursor-from-cache :path [:meetings]
                     :contact-server-fn (partial meeting-api/list-meetings max)
                     :server-result-path [:data :result]
                     :result-strategy :append
                     :client-receive-fn receive-fn))

(defn cache-documents [max & [receive-fn]]
  (cursor-from-cache :path [:documents]
                     :contact-server-fn (partial document-api/list-documents max)
                     :server-result-path [:data :result]
                     :result-strategy :append
                     :client-receive-fn receive-fn))

(defn cache-cal-items [days & [receive-fn]]
  (cursor-from-cache :path [:cal-items]
                     :contact-server-fn (partial calendar-api/list-cal-items days)
                     :server-result-path [:data :result]
                     :result-strategy :append
                     :client-receive-fn receive-fn))

(defn cache-knowledgebase [max & [receive-fn]]
  (cursor-from-cache :path [:knowledgebase]
                     :contact-server-fn (partial knowledgebase-api/list-knowledge-entries max)
                     :server-result-path [:data :result]
                     :result-strategy :append
                     :client-receive-fn receive-fn))

;; FIXME: Do not create CURSOR
(defn cache-admin-users [max & [receive-fn]]
  (cursor-from-cache :path [:admin :users]
                     :contact-server-fn (partial admin-api/list-users max)
                     :server-result-path [:data :users]
                     :result-strategy :append
                     :client-receive-fn receive-fn))



#_(let [invoices (r/cursor db/app-db [:invoices])]

   (if (empty? @invoices)
     (invoice-api/list-invoices
       (fn [result]
          ;(enc/logf "Received %s" result)
         (reset! invoices (get-in result [:data :invoices]))
         (when fn? receive-fn
                   (receive-fn invoices))))
     (do
       (log/info "from cache")
       (when fn? receive-fn
                 (receive-fn invoices)))))



;;(r/cursor db/app-db [:invoices])