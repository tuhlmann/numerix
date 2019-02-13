(ns numerix.events.user
  (:require [taoensso.timbre :as log]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [numerix.history :as hist]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [numerix.api.project :as project-api]
            [numerix.api.membership :as membership-api]
            [numerix.views.common-controls :as ctrl]
            [numerix.api.crud-ops :as crud-ops]))


(rf/reg-fx
  :user-api-fn
  (fn [[fn-id user]]
    (case fn-id
      :save-user
      (user-api/save-user-data user)

      :reset-user
      (user-api/reset-user-data user)

      :change-user-password
      (user-api/change-user-password user)

      :remove-profile-image
      (user-api/remove-profile-image
        (fn [response]
          (let [user (get-in response [:data :user])]
            (rf/dispatch [:user/remove-profile-image-success user]))))

      )))


(rf/reg-fx
  :project-api-fn
  (fn [[fn-id project]]
    (case fn-id

      :activate-project
      (project-api/activate-project
        project
        (fn [response]
          ;(log/info "activate project response " (pr-str response))
          (rf/dispatch [:project/activate-project-success])))

      )))

(rf/reg-fx
  :membership-api-fn
  (fn [[fn-id data]]
    (case fn-id

      :invite
      (let [{:keys [invitation]} data]
        (membership-api/invite-members
          invitation
          (fn [response]
            (let [memberships (get-in response [:data :result])]
              (rf/dispatch [:membership/invite-success memberships])
              ))))

      )))

(rf/reg-fx
  :management-api-fn
  (fn [[fn-id data]]
    (case fn-id

      :get-and-swap-initial-data
      (let [{:keys [callback]} data]
        (user-api/get-initial-data
          (fn [response]
            (rf/dispatch [:management/get-initial-data-success response callback]))))

      :send-contact-message
      (user-api/post-contact-message
        data
        #(rf/dispatch [:user/send-contact-message-success]))

      :push-view-settings
      (let [{:keys [view-state]} data]
        (user-api/push-view-settings view-state))

      :callback
      (data)

      )))


;;; EVENTS TRIGGERED BY THE VIEW

(rf/reg-event-fx
  :user/save-user
  (fn [cofx [_ user]]
    {:db (:db cofx)
     :user-api-fn [:save-user user]}))

(rf/reg-event-fx
  :user/send-contact-message
  (fn [cofx [_ contact-msg]]
    {:db (:db cofx)
     :management-api-fn [:send-contact-message contact-msg]}))

(rf/reg-event-fx
  :user/change-user-password
  (fn [cofx [_ user]]
    {:db (:db cofx)
     :user-api-fn [:change-user-password user]}))

(rf/reg-event-fx
  :user/reset-user
  (fn [cofx [_ user]]
    {:db (:db cofx)
     :user-api-fn [:reset-user user]}))

(rf/reg-event-fx
  :user/remove-profile-image
  (fn [{db :db} _]
    (let [new-db (s/assoc-in-user db :profile-image-id nil)]
      {:db new-db
       :user-api-fn [:remove-profile-image]})))

(rf/reg-event-db
  :user/current-user-field-change
  (fn [db [_ k v]]
    (let [new-db (s/assoc-in-user db k v)]
      new-db)))

(rf/reg-event-fx
  :project/activate
  (fn [cofx [_ project]]
    {:db (:db cofx)
     :project-api-fn [:activate-project project]}))

(rf/reg-event-fx
  :memberhip/invite
  (fn [cofx [_ invitation]]
    {:db (:db cofx)
     :membership-api-fn [:invite {:invitation invitation}]}))


;;; EVENTS TRIGGERED AS RESPONSE TO AN INCOMING EVENTS

(rf/reg-event-db
  :membership/invite-success
  (fn [db [_ memberships]]
    (let [master-data (crud-ops/add-or-replace-records-in (s/master-data db) memberships)
          form-state (dissoc (s/form-state db) :selected-item)
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data))
          new-db (ctrl/show-details-view-item new-db false)]

      new-db)))


(rf/reg-event-fx
  :user/update-user-success
  (fn [cofx [_ user]]
    (let [new-db (s/assoc-user (:db cofx) user)]
      {:db new-db
       :history-api-fn [:go-to-home]})))

(rf/reg-event-fx
  :user/change-user-password-success
  (fn [cofx [_ {:keys [type timeout msg] :as alert}]]
    (let [new-db (if-not (empty? alert)
                   (s/add-alert2 (:db cofx) alert)
                   (:db cofx))]
      {:db new-db
       :history-api-fn [:go-to-home]})))

(rf/reg-event-fx
  :user/remove-profile-image-success
  (fn [cofx [_ user]]
    (let [new-db (s/dissoc-in-form-data (:db cofx) :profile-image-id)]
      {:db new-db})))

(rf/reg-event-fx
  :user/send-contact-message-success
  (fn [cofx [_]]
    (let [new-db (s/add-info-alert-in (:db cofx) "Thank you for contacting us!")]
      {:db new-db
       :history-api-fn [:go-to-home]})))

(rf/reg-event-fx
  :project/activate-project-success
  (fn [cofx _]
    (let [new-db (s/unload-all-data (:db cofx))]
      {:db new-db
       :management-api-fn [:get-and-swap-initial-data {:callback reagent/force-update-all}]})))

(rf/reg-event-fx
  :management/get-and-swap-initial-data
  (fn [cofx [_ callback]]
    (let [new-db (s/unload-all-data (:db cofx))]
      {:db new-db
       :management-api-fn [:get-and-swap-initial-data {:callback callback}]})))

(rf/reg-event-db
  :management/set-csrf-token
  (fn [db [_ token]]
    (assoc-in db [:post-config :csrf-token] token)))


(rf/reg-event-fx
  :management/get-initial-data-success
  (fn [cofx [_ loaded-data callback]]
    (let [new-db (-> (:db cofx)
                     (dissoc :documents :timeroll :invoices :knowledgebase)
                     (assoc :server-config (get-in loaded-data [:data :server-config]))
                     (assoc :user (get-in loaded-data [:data :user]))
                     (assoc :my-memberships (get-in loaded-data [:data :my-memberships]))
                     (assoc :notifications (get-in loaded-data [:data :notifications]))
                     (assoc :projects (get-in loaded-data [:data :projects]))
                     (assoc :memberships (get-in loaded-data [:data :memberships]))
                     (assoc :contacts (get-in loaded-data [:data :contacts]))
                     (assoc :textblocks (get-in loaded-data [:data :textblocks]))
                     (update :view-state merge (get-in loaded-data [:data :view-settings])))]

      (js/setTimeout callback)

      {:db new-db})))


