(ns numerix.events.notification
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf :refer [dispatch]]
            [numerix.state :as s]
            [numerix.ws :as socket]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d]
            [numerix.views.common-controls :as ctrl]
            [numerix.site :as site]
            [clojure.string :as str]
            [taoensso.encore :as enc]
            [numerix.api.crud-ops :as crud-ops]))

(rf/reg-event-db
  :related-comments/add-comments-to-db
  (fn [db [_ related-type related-id msg-result append-or-prepend?]]
    (let [new-db (update-in db [:comment-container related-id]
                            (fn [container]
                              (let [new-msgs (if (= append-or-prepend? :append)
                                               (concat (:messages container) (:items msg-result))

                                               (concat (:items msg-result) (:messages container)))]
                                (merge container
                                       {:status (if (= (:count msg-result) 0) :all-loaded :idle)
                                        :messages (vec new-msgs)}))))]
      new-db)))


(rf/reg-event-db
  :related-comments/new-comment-from-server
  (fn [db [_ comment]]
    (enc/if-let [comments (get-in db [:comment-container (:related-id comment) :messages])
                 new-comments (into [] (conj comments comment))
                 new-db (-> db
                            (assoc-in [:comment-container (:related-id comment) :messages] new-comments)
                            )]
                new-db

                db)))

(rf/reg-event-db
  :related-comments/removed-comment-from-server
  (fn [db [_ comment-stub]]
    (enc/if-let [comments (get-in db [:comment-container (:related-id comment-stub) :messages])
                 new-comments (crud-ops/remove-listed-record-in comments comment-stub)
                 new-db (-> db
                            (assoc-in [:comment-container (:related-id comment-stub) :messages] new-comments)
                            )]
                new-db

                db)))


(rf/reg-event-db
  :chat/add-chat-messages-to-db
  (fn [db [_ chat-room-id msg-result append-or-prepend?]]
    (let [new-db (update-in db [:chat-room-msg-cache chat-room-id]
                            (fn [room]
                              (let [new-msgs (if (= append-or-prepend? :append)
                                               (concat (:messages room) (:items msg-result))

                                               (concat (:items msg-result) (:messages room)))]
                                (merge room
                                       {:status (if (= (:count msg-result) 0) :all-loaded :idle)
                                        :messages (vec new-msgs)}))))]
      new-db)))


(rf/reg-event-db
  :chat/new-chat-msg-from-server
  (fn [db [_ message]]
    (enc/if-let [;chat-room-id (s/get-in-form-data db :__chat-room-id)
                 ;_ (= chat-room-id (:chat-room-id message))
                 chat-room-id (:chat-room-id message)
                 chat-messages (get-in db [:chat-room-msg-cache chat-room-id :messages])
                 msgs (into [] (conj chat-messages message))
                 new-db (-> db
                            (assoc-in [:chat-room-msg-cache chat-room-id :messages] msgs)
                            )]

                new-db

                db)))

(rf/reg-event-db
  :chat/removed-msg-from-server
  (fn [db [_ message-stub]]
    (enc/if-let [chat-room-id (:chat-room-id message-stub)
                 chat-messages (get-in db [:chat-room-msg-cache chat-room-id :messages])
                 msgs (crud-ops/remove-listed-record-in chat-messages message-stub)
                 new-db (-> db
                            (assoc-in [:chat-room-msg-cache chat-room-id :messages] msgs)
                            )]
                new-db

                db)))


(rf/reg-event-db
  :presence/user-is-online
  (fn [db [_ presence]]
    ;(log/info ":presence/user-is-online " presence)
    (if-let [membership (first (filter #(= (:email %) (:email presence)) (:memberships db)))]
      (let [new-presences (crud-ops/add-or-replace-record-in (:__presences membership) presence)
            new-membership (-> membership
                               (assoc :__presences new-presences)
                               (assoc :__online-status :online))
            new-memberships (crud-ops/add-or-replace-record-in (:memberships db) new-membership)
            new-db (assoc db :memberships new-memberships)]
        ;(log/info "online, new membership is " new-membership)
        new-db)
      db)))

(rf/reg-event-db
  :presence/user-is-offline
  (fn [db [_ presence]]
    ;(log/info ":presence/user-is-offline " presence)
    (if-let [membership (first (filter #(= (:email %) (:email presence)) (:memberships db)))]
      (let [new-presences (crud-ops/remove-listed-record-in (:__presences membership) presence)
            new-membership (-> membership
                               (assoc :__presences new-presences)
                               (assoc :__online-status (if (empty? new-presences) :offline :online)))
            new-memberships (crud-ops/add-or-replace-record-in (:memberships db) new-membership)
            new-db (assoc db :memberships new-memberships)]
        ;(log/info "offline, new membership is " new-membership)
        new-db)
      db)))

(rf/reg-event-fx
  :presence/log-user-presence
  (fn [{db :db} [_ related-type related-id]]
    ;(log/info ":presence/log-user-presence " related-type " -> " related-id)
    {
     :db db
     :presence-api-fn [:presence/log-user-presence {:related-type related-type
                                                    :related-id related-id}]
     }))

(rf/reg-event-db
  :presence/remote-presence-update
  (fn [db [_ presence]]
    ;(log/info ":presence/remote-presence-update " presence)
    (if-let [membership (first (filter #(= (:email %) (:email presence)) (:memberships db)))]
      (let [new-presences (crud-ops/add-or-replace-record-in (:__presences membership) presence)
            new-membership (-> membership
                               (assoc :__presences new-presences)
                               (assoc :__online-status :online))
            new-memberships (crud-ops/add-or-replace-record-in (:memberships db) new-membership)
            new-db (assoc db :memberships new-memberships)]
        ;(log/info "online, new membership is " new-membership)
        new-db)
      db)))


(rf/reg-event-db
  :cleanup-db
  (fn [db [_ path]]
    (h/dissoc-in db path)))

(rf/reg-event-fx
  :remote-subscription/subscribe
  (fn [{db :db} [_ subscription]]
    (log/info ":remote-subscription/subscribe " subscription)
    {:db db
     :remote-subscription-api-fn [:subscribe subscription]}))

(rf/reg-event-fx
  :remote-subscription/cancel
  (fn [{db :db} [_ subscription]]
    (log/info ":remote-subscription/cancel " subscription)
    {:db db
     :remote-subscription-api-fn [:cancel subscription]}))


(rf/reg-event-fx
  :notification/new-notification-from-server
  (fn [{db :db} [_ notification]]
    (let [notifications (into [] (cons notification (get db :notifications)))
          new-db (assoc db :notifications notifications)]

      {:db new-db
       :notification-api-fn [:browser-notification notification]
       })))

(rf/reg-event-fx
  :notification/updated-notification-from-server
  (fn [{db :db} [_ notification]]
    (let [notifications (crud-ops/add-or-replace-record-in (get db :notifications) notification)
          new-db (assoc db :notifications notifications)]

      {:db new-db})))

(rf/reg-event-db
  :notification/remove-all-notifications
  (fn [db [_ notification]]
    (assoc db :notifications [] )))

(rf/reg-event-fx
  :notification/toggle-notifications-widget
  (fn [{db :db} [_]]
    (let [show? (not (boolean (s/get-in-form-state db :show-notifications-widget)))]
      {:db (s/assoc-in-form-state db :show-notifications-widget show?)
       :dispatch [:mouse-inside-detail-area-no-follow show?]})))

(rf/reg-event-fx
  :notification/close-notifications-widget
  (fn [{db :db} [_]]
    {:db (s/assoc-in-form-state db :show-notifications-widget false)
     :dispatch [:mouse-inside-detail-area-no-follow false]}))

(rf/reg-event-fx
  :notification/mark-as-read
  (fn [{db :db} [_ notification]]
    {:db db
     :notification-api-fn [:mark-read notification]}))

(rf/reg-event-fx
  :notification/remove-all
  (fn [{db :db} _]
    {:db db
     :notification-api-fn [:remove-all]}))

