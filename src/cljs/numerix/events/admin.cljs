(ns numerix.events.admin
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf :refer [dispatch]]
            [numerix.state :as s]
            [numerix.ws :as socket]
            [numerix.api.admin :as admin-api]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d]
            [numerix.views.common-controls :as ctrl]
            [numerix.api.cache :as cache]
            [numerix.views.common :as c]
            [numerix.site :as site]
            [clojure.string :as str]
            [numerix.api.crud-ops :as crud-ops]))

(defn add-or-replace-user-in [master-data user]
  (if-let [pos (first (h/positions #(= (:_id %) (:_id user)) master-data))]
    (assoc master-data pos user)
    (into [] (cons user master-data))))


;;; EFFECT HANDLERS

(rf/reg-fx
  :admin-api-fn
  (fn [[fn-id user]]
    (case fn-id
      :save-user
      (admin-api/save-user
        user
        (fn [response]
          ;(log/info "save response " (pr-str response))
          (let [user (get-in response [:data :user])]
            (dispatch [:admin/update-user-success user]))))

      :remove-user
      (admin-api/remove-user
        user
        (fn [response]
          ;(log/info "remove response " (pr-str response))
          (when (socket/response-ok response)
            (dispatch [:admin/remove-user-success user]))))

      :send-pwd-reset
      (admin-api/reset-pwd-token
        user
        (fn [response]
          (let [user (get-in response [:data :user])]
            (dispatch [:admin/user-action-success user]))))

      :clear-login-token
      (admin-api/clear-login-token
        user
        (fn [response]
          (let [user (get-in response [:data :user])]
            (dispatch [:admin/user-action-success user]))))

      )))


;;; INIT PAGES

;; Initial Admin Users Page state
(rf/reg-event-fx
  :init-admin-users-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    {:db (-> db
             (s/assoc-master-config master-config)
             (s/assoc-form-state (merge (s/form-state db) form-state)))

     :load-and-cache [:admin/user-list 1000]}))

(rf/reg-event-fx
  :admin/load-user-list-success
  (fn [{db :db} [_ user-list]]
    (let [new-db (-> db
                     (s/assoc-master-data user-list)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item user-list)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
  )))

;;; EVENTS TRIGGERED BY THE VIEW

(rf/reg-event-fx
  :admin/save-user
  (fn [cofx [_ user]]
    {
     :db (:db cofx)
     :admin-api-fn [:save-user user]}))

(rf/reg-event-fx
  :admin/remove-user
  (fn [cofx [_ user]]
    {
     :db (:db cofx)
     :admin-api-fn [:remove-user user]}))

(rf/reg-event-fx
  :admin/send-pwd-reset
  (fn [cofx [_ user]]
    {
     :db (:db cofx)
     :admin-api-fn [:send-pwd-reset user]}))

(rf/reg-event-fx
  :admin/clear-login-token
  (fn [cofx [_ user]]
    {
     :db (:db cofx)
     :admin-api-fn [:clear-login-token user]}))

;;; EVENTS TRIGGERED AS RESPONSE TO AN INCOMING EVENTS

(rf/reg-event-db
  :admin/update-user-success
  (fn [db [_ user]]
    (let [master-data (add-or-replace-user-in (s/master-data db) user)
          form-state (assoc (s/form-state db) :selected-item user)
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data))
          new-db (ctrl/show-details-view-item new-db false)]

      new-db)))

(rf/reg-event-db
  :admin/user-action-success
  (fn [db [_ user]]
    (let [master-data (add-or-replace-user-in (s/master-data db) user)
          form-state (assoc (s/form-state db) :selected-item user)
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data))
          new-db (ctrl/show-details-view-item new-db false)]

      new-db)))

(rf/reg-event-db
  :admin/remove-user-success
  (fn [db [_ user]]
    (log/info "in remove success")
    (let [master-data (crud-ops/remove-listed-record-in (s/master-data db) user)
          form-state (dissoc (s/form-state db) :selected-item)
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data))]

      new-db)))

