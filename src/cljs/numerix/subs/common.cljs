(ns numerix.subs.common
  (:require [taoensso.timbre :as log]
            [reagent.ratom :as r]
            [re-frame.core :as rf :refer [dispatch]]
            [numerix.state :as s]
            [numerix.ws :as socket]
            [numerix.api.admin :as admin-api]
            [numerix.api.chat :as chat-api]
            [numerix.api.comment :as comment-api]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d]
            [numerix.views.common-controls :as ctrl]
            [numerix.api.cache :as cache]
            [numerix.views.common :as c]
            [numerix.site :as site]
            [numerix.auth.form-auth :as form-auth]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]))

(rf/reg-sub
  :master-config
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    (get db :master-config)))

(rf/reg-sub
  :master-data
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    (get-in db [:master-config :master-data])))

(rf/reg-sub
  :form-state
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    (get db :form-state)))

(rf/reg-sub
  :form-state-selected-item
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    (get-in db [:form-state :selected-item])))

(rf/reg-sub
  :form-state-selected-item-start-date
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    (get-in db [:form-state :selected-item-start-date])))

(rf/reg-sub
  :form-state-errors
  (fn [db [_ & error-path]]
    (s/get-in-form-state db (into [:errors] (remove nil? error-path)))))

(rf/reg-sub
  :master-list-filter
  (fn [db _]
    (s/get-in-form-state db :master-list-filter)))

(rf/reg-sub
  :get-window-state
  (fn [db _]
    (:window-state db)))

(rf/reg-sub
  :form-data-pre-delete
  (fn [db _]
    (s/get-in-form-state db :form-data-pre-delete)))

(rf/reg-sub
  :form-config
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    (get db :form-config)))

(rf/reg-sub
  :form-data
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    ;(log/info "get form-data " (pr-str (get-in db [:form-config :form-data])))
    (get-in db [:form-config :form-data])))

(rf/reg-sub
  :form-data-allowed-access
  (fn [query-v _]
    [(rf/subscribe [:current-membership])])

  ;; computation function
  (fn [[membership] [_ form-name]]
    (form-auth/as-form-access? form-name membership)))

(rf/reg-sub
  :form-data-allowed-read
  (fn [query-v _]
    [(rf/subscribe [:current-membership])])

  ;; computation function
  (fn [[membership] [_ form-name]]
    (form-auth/as-form-read? form-name membership)))

(rf/reg-sub
  :form-data-allowed-create-new
  (fn [query-v _]
    [(rf/subscribe [:form-state])
     (rf/subscribe [:current-user])
     (rf/subscribe [:current-membership])
     (rf/subscribe [:my-memberships])])

  ;; computation function
  (fn [[form-state user membership my-memberships] _]
    (let [permission-name (or (:permission-name form-state) (:form-name form-state))]

      (case permission-name
        :projects
        (form-auth/as-form-create-new-project? (:_id user))

        :memberships
        (form-auth/as-form-create-new-membership? membership (:_id user))

        ;; We send the form-data in twice
        ;; in the case of client side authorization this is unnecessary, but when using this function from the server
        ;; we instead pass in the existing record from the DB.
        (form-auth/as-form-create-new? permission-name membership (:_id user))))))

(rf/reg-sub
  :form-data-allowed-edit
  (fn [query-v _]
    [(rf/subscribe [:form-state])
     (rf/subscribe [:form-data])
     (rf/subscribe [:current-user])
     (rf/subscribe [:current-membership])
     (rf/subscribe [:my-memberships])])

  ;; computation function
  (fn [[form-state form-data user membership my-memberships] _] ;; that 1st parameter is a 2-vector of values
    (let [permission-name (or (:permission-name form-state) (:form-name form-state))]

      (case permission-name
        :projects
        (if (:_id form-data)
          (if-let [my-membership (first (filter #(= (:_id form-data) (:project-id %)) my-memberships))]
            (form-auth/as-form-edit-project? my-membership (:_id user) form-data form-data)
            false)

          (form-auth/as-form-create-project? (:_id user) form-data))

        :memberships
        (if-not (:_id form-data)
          (form-auth/as-form-create-membership? membership (:_id user) form-data)
          (form-auth/as-form-edit-membership? membership (:_id user) form-data form-data))

        ;; We send the form-data in twice
        ;; in the case of client side authorization this is unnecessary, but when using this function from the server
        ;; we instead pass in the existing record from the DB.
        (form-auth/as-form-edit? permission-name membership (:_id user) form-data form-data)))))

(rf/reg-sub
  :form-data-allowed-remove
  (fn [query-v _]
    [(rf/subscribe [:form-state])
     (rf/subscribe [:form-data])
     (rf/subscribe [:current-user])
     (rf/subscribe [:current-membership])
     (rf/subscribe [:my-memberships])])

  ;; computation function
  (fn [[form-state form-data user membership my-memberships] _] ;; that 1st parameter is a 2-vector of values
    (let [permission-name (or (:permission-name form-state) (:form-name form-state))]
      (case permission-name
        :projects
        (if-let [my-membership (first (filter #(= (:_id form-data) (:project-id %)) my-memberships))]
          (form-auth/as-form-remove-project? my-membership (:_id user) (:current-project user) form-data)
          false)

        :memberships
        (if-not (:_id form-data)
          (form-auth/as-form-create-membership? membership (:_id user) form-data)
          (form-auth/as-form-edit-membership? membership (:_id user) form-data form-data))

        (form-auth/as-form-remove? permission-name membership (:_id user) form-data)))))

(rf/reg-sub
  :has-membership-permission
  (fn [query-v _]
    [(rf/subscribe [:current-membership])])

  ;; computation function
  (fn [[membership] [_ perm]]
    (form-auth/has-permission? membership perm)))

(rf/reg-sub
  :form-data-field
  (fn [db [_ path]]
    (s/get-in-form-data db path)))

(rf/reg-sub
  :form-state-field
  (fn [db [_ path]]
    (s/get-in-form-state db path)))

(rf/reg-sub
  :session-field
  (fn [db [_ path dflt]]
    (if-let [field (s/get-in-session db path)]
      field
      dflt)))

(rf/reg-sub
  :post-config-field
  (fn [db [_ path]]
    (s/get-in-post-config db path)))

(rf/reg-sub
  :current-user
  (fn [db [_ path]]
    (:user db)))

(rf/reg-sub
  :my-memberships
  (fn [db [_ path]]
    (:my-memberships db)))

(rf/reg-sub
  :current-membership
  (fn [query-v _]
    [(rf/subscribe [:current-user])
     (rf/subscribe [:my-memberships])])

  ;; computation function
  (fn [[user my-memberships] [_ perm]]
    (first (filter #(= (:current-project user) (:project-id %)) my-memberships))))

(rf/reg-sub
  :view-state
  (fn [db [_ path]]
    (:view-state db)))

(rf/reg-sub
  :alerts
  (fn [db [_ path]]
    (:alerts db)))

(rf/reg-sub
  :textblocks
  (fn [db [_ path]]
    (:textblocks db)))

(rf/reg-sub
  :projects
  (fn [db [_ path]]
    (:projects db)))

(rf/reg-sub
  :memberships
  (fn [db [_ path]]
    (:memberships db)))

(rf/reg-sub
  :notifications
  (fn [db [_ path]]
    (:notifications db)))

(rf/reg-sub
  :memberships-sort-by-name
  (fn [query-v _]
    [(rf/subscribe [:memberships])])

  ;; computation function
  (fn [[memberships] _]
    (sort-by :__user-name memberships)))

(rf/reg-sub-raw
  :chat-room-msg-cache
  (fn [app-db [_ chat-room-id]]
    ;; Cache last 3 chats
    (if-not (get-in @app-db [:chat-room-msg-cache chat-room-id])
      (do
        (when (> (count (get-in @app-db [:chat-room-msg-cache])) 3)
          ;; FIXME: Remove chat not visited for the longest time (LRU)
          (let [[room-id _] (first (sort-by (fn [it] (:ts (second it))) (into [] (get-in @app-db [:chat-room-msg-cache]))))]
            (log/info "removing room id " room-id)
            (rf/dispatch [:remote-subscription/cancel {:sub-type :chat-room
                                                       :sub-id   chat-room-id}])
            (swap! app-db h/dissoc-in [:chat-room-msg-cache room-id])))
        (rf/dispatch [:remote-subscription/subscribe {:sub-type :chat-room
                                                      :sub-id   chat-room-id}])

        (swap! app-db assoc-in [:chat-room-msg-cache chat-room-id] {:ts  (tc/to-long (t/now))
                                                           :status :loading
                                                           :messages []})
        (chat-api/load-chat-messages
          {:chat-room-id chat-room-id}
          (fn [response]
            (rf/dispatch [:chat/add-chat-messages-to-db
                          chat-room-id
                          (get-in response [:data :result])
                          :append]))))

      ;; Renew recent access
      (swap! app-db assoc-in [:chat-room-msg-cache chat-room-id :ts] (tc/to-long (t/now))))

    (r/make-reaction
      (fn [] (get-in @app-db [:chat-room-msg-cache chat-room-id] {})))))


(rf/reg-sub-raw
  :related-comments
  (fn [app-db [_ related-type related-id]]
    ;; Cache last 3 comment areas
    (if-not (get-in @app-db [:comment-container related-id])
      (do
        (when (> (count (get-in @app-db [:comment-container])) 3)
          (let [[container-id _] (first (sort-by (fn [it] (:ts (second it))) (into [] (get-in @app-db [:comment-container]))))]
            (log/info "removing container id " container-id)
            (rf/dispatch [:remote-subscription/cancel {:sub-type related-type
                                                       :sub-id   related-id}])
            (swap! app-db h/dissoc-in [:comment-container related-id])))
        (rf/dispatch [:remote-subscription/subscribe {:sub-type related-type
                                                      :sub-id   related-id}])

        (swap! app-db assoc-in [:comment-container related-id] {:ts  (tc/to-long (t/now))
                                                                :status :loading
                                                                :messages []})

        (comment-api/load-comments
          {:related-type related-type
           :related-id related-id}
          (fn [response]
            (rf/dispatch [:related-comments/add-comments-to-db
                          related-type
                          related-id
                          (get-in response [:data :result])
                          :append]))))

      ;; Renew recent access
      (swap! app-db assoc-in [:comment-container related-id :ts] (tc/to-long (t/now))))

    (r/make-reaction
      (fn [] (get-in @app-db [:comment-container related-id] {})))))


