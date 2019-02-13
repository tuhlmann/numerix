(ns numerix.events.calendar
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf :refer [dispatch]]
            [numerix.state :as s]
            [numerix.ws :as socket]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d]
            [numerix.views.common-controls :as ctrl]
            [numerix.api.calendar :as calendar-api]
            [numerix.views.common :as c]
            [numerix.site :as site]
            [clojure.string :as str]
            [numerix.api.crud-ops :as crud-ops]))


;; Used to load data from the server and cache it locally
(rf/reg-fx
  :load-selected-cal-item-start-date
  (fn [[selected-item-id success-fn]]
      (calendar-api/get-item-start-date selected-item-id success-fn)))


(rf/reg-event-fx
  :cal-items/load-list-success
  (fn [{db :db} [_ cal-item-list]]
    (let [new-db (-> db
                     (s/assoc-master-data cal-item-list)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item cal-item-list)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
      )))


(rf/reg-event-fx
  :cal-items/load-selected-cal-item-start-date-success
  (fn [{db :db} [_ selected-item-start-date]]
    (let [new-db (s/assoc-in-form-state db :selected-item-start-date selected-item-start-date)]

      {:db new-db})))


