(ns numerix.api.crud-ops
  (:require [taoensso.timbre      :as log]
            [numerix.ws           :as socket]
            [numerix.state        :as s]
            [numerix.lib.helpers  :as h]
            [cljs-time.core       :refer [now local-date-time days minus day-of-week date-time]]))

(defn new-record
  ([user]
   (new-record user nil))
  ([user extra-params]
    ;(log/info "Current user id " (user-api/user-id user))
   (merge
     {:author-id (:_id user)
      :project-id (:current-project user)
      :created (now)
      } extra-params)))

(defn add-or-replace-record-in [master-data record-with-id]
  (if-let [pos (first (h/positions #(= (:_id %) (:_id record-with-id)) master-data))]
    (assoc master-data pos record-with-id)
    (into [] (cons record-with-id master-data))))

(defn add-or-replace-records-in
  "Takes a seq of new records and adds them to the master-data array"
  [master-data records-with-id]
  (reduce add-or-replace-record-in master-data records-with-id))

(defn remove-listed-record-in [master-data record-with-id]
  (filterv (fn [it]
             ;(log/infof "Compare %s and %s" (:_id it) (:_id record-with-id))
             (not= (:_id it) (:_id record-with-id))) master-data))

(defn remove-listed-records-in [master-data records-with-id]
  (reduce remove-listed-record-in master-data records-with-id))


(defn send-ident [type op]
  (keyword (str (name type) "/" (name op) )))

(defn execute-remote-op [type op record-with-id success-fn]
  (socket/send! [(send-ident type op) record-with-id] 5000
                (fn [response]
                  ;(log/info "response is " (pr-str response))
                  (if (socket/response-ok (last response))
                    (success-fn (last response))))))

(defn switch-project [type record-with-id project success-fn]
  (socket/send! [(send-ident type :switch-project) {type (h/ref-or-val record-with-id) :project project}] 5000
                (fn [response]
                  ;(log/info "switch project response is " (pr-str response))
                  (if (socket/response-ok (last response))
                    (success-fn (last response))))))
