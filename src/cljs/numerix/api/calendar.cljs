(ns numerix.api.calendar
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [numerix.views.common :as c]
            [numerix.api.crud-ops :as crud-ops]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]))


;(defn dates->moment [cal-item]
;  (assoc cal-item :start (js/moment (tc/to-date (:start cal-item)))
;                  :end (js/moment (tc/to-date (:end cal-item)))))

(def cal-item-fields [:start :end :allDay :_id :author-id :project-id :created :title :text])

(defn dates->moment [cal-item]
  (assoc cal-item :start (tc/to-string (:start cal-item))
                  :end (tc/to-string (:end cal-item))))

(defn moment->dates [cal-item]
  (assoc cal-item :start (tc/from-date (.toDate (:start cal-item)))
                  :end (tc/from-date (.toDate (:end cal-item)))))


(defn strip-client-fields [cal-item]
  (log/info "event " (pr-str cal-item))
  (select-keys cal-item cal-item-fields))

(defn list-cal-items [days success-fn]
  (socket/send! [:cal-item/list days] 5000
                (fn [response]
                  ;(log/info "list cal-items result " (pr-str response))
                  (success-fn (last response)))))


(defn fetch-cal-items-for-range [start end success-fn]
  (socket/send! [:cal-item/list-range {:start (tc/from-long start)
                                       :end (tc/from-long end)}] 5000
                (fn [response]
                  ;(log/info "list cal-items range result " (pr-str (get-in (last response) [:data :result])))
                  (let [re (mapv dates->moment (get-in (last response) [:data :result]))]
                    (success-fn (clj->js re)))
                  )))

(defn get-item-start-date [item-id success-fn]
  (socket/send! [:cal-item/get-item-start-date item-id] 5000
                (fn [response]
                  (log/info ":cal-item/get-item-start-date " (pr-str response))
                  (success-fn (get-in (last response) [:data :result])))))

