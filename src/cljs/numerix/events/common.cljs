(ns numerix.events.common
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [numerix.state :as s]
            [numerix.views.common :as c]
            [numerix.views.common-controls :as ctrl]
            [numerix.api.crud-ops :as crud-ops]
            [numerix.api.chat :as chat-api]
            [numerix.api.comment :as comment-api]
            [numerix.api.notification :as notification-api]
            [numerix.ws :as socket]
            [validateur.validation :as v]
            [numerix.lib.helpers :as h]
            [numerix.history :as history]
            [numerix.site :as site]
            [taoensso.encore :as enc]
            [cljs-time.core :as t]))

;; BEGIN re-frame events

(rf/reg-event-fx
  :show-details-add-item
  (fn [{db :db} _]
    {:db (-> db
             (s/assoc-in-form-state :edit-item true)
             (s/assoc-in-form-state :add-new true)
             (ctrl/show-detail-area true)
             )

     :dispatch [:common/detail-only-view true]}))

(rf/reg-event-fx
  :select-item-and-show-details
  (fn [{db :db} [_ selected-item]]
    (let [result
          {:db (-> db
                   (s/assoc-in-form-state :edit-item false)
                   (s/assoc-in-form-state :show-details true)
                   ;(s/assoc-in-form-state :detail-only-view true)
                   (s/assoc-in-form-state :selected-item selected-item))
           }
          result (if-let [link-fn (:master-list-item-std-detail-link (s/master-config db))]
                   (assoc result :history-api-fn [:navigate {:route (link-fn selected-item)}])
                   result)]

      result)))

(rf/reg-event-fx
  :select-item-and-edit-details
  (fn [{db :db} [_ selected-item]]
    (let [result
          {:db (-> db
                   (s/assoc-in-form-state :edit-item true)
                   (s/assoc-in-form-state :show-details true)
                   (s/assoc-in-form-state :selected-item selected-item))
           }
          result (if-let [link-fn (:master-list-item-std-detail-link (s/master-config db))]
                   (assoc result :history-api-fn [:navigate {:route (link-fn selected-item)}])
                   result)]

      result)))

(rf/reg-event-db
  :unset-selected-item
  (fn [db _]
    (s/dissoc-in-form-state db :selected-item)))

(rf/reg-event-db
  :set-selected-item
  (fn [db [_ selected-item]]
    (s/assoc-in-form-state db :selected-item selected-item)))

(rf/reg-event-db
  :mouse-inside-detail-area-no-follow
  (fn [db [_ dont-follow-mouse?]]
    (s/assoc-in-form-state db :mouse-inside-detail-area-no-follow dont-follow-mouse?)))

(rf/reg-event-db
  :mouse-inside-detail-area
  (fn [db [_ inside?]]
    (if (s/get-in-form-state db :mouse-inside-detail-area-no-follow)
      db

      (s/assoc-in-form-state db :mouse-inside-detail-area inside?))))

(rf/reg-event-db
  :show-detail-area
  (fn [db [_ show?]]
    (ctrl/show-detail-area db show?)))

(rf/reg-fx
  :crud-ops-api-fn
  (fn [[fn-id args]]
    (case fn-id

      :crud-ops-api/get
      (let [{:keys [type op record]} args]
        (crud-ops/execute-remote-op
          type
          op
          (select-keys record [:_id :author-id])
          (fn [response]
            ;(log/info "crud-ops GET response " (pr-str response))
            (when (socket/response-ok response)
              (let [result-rec (get-in response [:data :result])]
                (rf/dispatch [:crud-ops/get-success {:type type :record result-rec }]))))))

      :crud-ops-api/save
      (let [{:keys [type op record next-fn]} args]
        (crud-ops/execute-remote-op
          type
          op
          record
          (fn [response]
            (let [result-rec (get-in response [:data :result])]
              (if (fn? next-fn)
                (next-fn result-rec
                         (fn [next-result-rec]
                           (rf/dispatch [:crud-ops/save-success {:type type :record next-result-rec}])))

                (rf/dispatch [:crud-ops/save-success {:type type :record result-rec}]))
              ))))

      :crud-ops-api/remove
      (let [{:keys [type op record-with-id next-fn]} args]
        (crud-ops/execute-remote-op
          type
          op
          (:_id record-with-id)
          (fn [response]
            (when (socket/response-ok response)
              (if (fn? next-fn)
                (next-fn record-with-id
                         (fn [next-result-rec]
                           (rf/dispatch [:crud-ops/remove-success {:type type :record record-with-id}])))

                (rf/dispatch [:crud-ops/remove-success {:type type :record record-with-id}]))))))

      :crud-ops-api/switch-project
      (let [{:keys [type record-with-id project]} args]
        (crud-ops/switch-project
          type
          record-with-id
          project
          (fn [response]
            ;(log/info "rf switch project response " (pr-str response))
            (rf/dispatch [:crud-ops/switch-project-success (:data response)])))

        )
      )))

(rf/reg-fx
  :history-api-fn
  (fn [[fn-id data]]
    (case fn-id

      :go-to-home
      (history/go-to-home)

      :navigate
      (let [{:keys [route]} data]
        (history/navigate! route)))))

(rf/reg-fx
  :chat-api-fn
  (fn [[fn-id data]]
    (case fn-id

      :chat-api/send-message
      (chat-api/send-msg data)

      :chat-api/remove-message
      (chat-api/remove-msg (:message-id data))

      :chat-api/load-messages
      (chat-api/load-chat-messages
        data
        (fn [response]
          (rf/dispatch [:chat/add-chat-messages-to-db
                        (:chat-room-id data)
                        (get-in response [:data :result])
                        :prepend])))

      )))

(rf/reg-fx
  :comment-api-fn
  (fn [[fn-id data]]
    (case fn-id

      :comment-api/send-comment
      (comment-api/send-comment data)

      :comment-api/remove-comment
      (comment-api/remove-comment (:comment-id data))

      )))


(rf/reg-fx
  :presence-api-fn
  (fn [[fn-id data]]
    (case fn-id

      ;:presence-api/heartbeat
      ;(chat-api/send-heartbeat (:form-name data) (:selected-item-id data))

      :presence/log-user-presence
      (chat-api/log-user-presence data)

      )))

(rf/reg-fx
  :remote-subscription-api-fn
  (fn [[fn-id subscription]]
    (case fn-id

      :subscribe
      (notification-api/subscribe subscription)

      :cancel
      (notification-api/cancel-subscription subscription)

      )))

(rf/reg-fx
  :notification-api-fn
  (fn [[fn-id notification]]
    (case fn-id

      :browser-notification
      (notification-api/check-browser-notification notification)

      :mark-read
      (notification-api/mark-notification-read notification)

      :remove-all
      (notification-api/remove-all-notifications)

      )))

(rf/reg-event-fx
  :go-to-home
  (fn [cofx _]
    {
     :db (:db cofx)
     :history-api-fn [:go-to-home]}))

(rf/reg-event-fx
  :navigate-to
  (fn [cofx [_ route]]
    {
     :db (:db cofx)
     :history-api-fn [:navigate {:route route}]}))


;; Initial Admin Users Page state
(rf/reg-event-fx
  :init-form-config
  (fn [{db :db} [_ form-config]]
    (let [new-db (s/assoc-form-config db form-config)
          re {:db new-db}]

      re
      ;(enc/if-let [form-name (get-in new-db [:form-state :form-name])
      ;             selected-item-id (get-in new-db [:form-state :selected-item :_id])]
      ;
      ;            (merge re {
      ;                       :chat-api-fn [:presence-api/heartbeat {:form-name form-name
      ;                                                              :selected-item-id selected-item-id}]
      ;                       })
      ;
      ;            re)

      )))

(rf/reg-event-db
  :assoc-in-form-config
  (fn [db [_ path v]]
    (s/assoc-in-form-config db path v)))

(rf/reg-event-db
  :dissoc-in-form-config
  (fn [db [_ & keys]]
    (reduce s/dissoc-in-form-config db keys)))

(rf/reg-event-db
  :init-form-state
  (fn [db [_ form-state]]
    (s/assoc-form-state db form-state)))

(rf/reg-event-db
  :assoc-in-form-state
  (fn [db [_ path v]]
    (s/assoc-in-form-state db path v)))

(rf/reg-event-db
  :dissoc-in-form-state
  (fn [db [_ & keys]]
    (reduce s/dissoc-in-form-state db keys)))

(rf/reg-event-db
  :dissoc-in-path
  (fn [db [_ path]]
    (h/dissoc-in db path)))

(rf/reg-event-db
  :assoc-form-data
  (fn [db [_ data]]
    (s/assoc-in-form-config db :form-data data)))

;; When a form-data field was changed due to editing this event handler is called
(rf/reg-event-db
  :form-data-field-change
  (fn [db [_ path v]]
    (s/assoc-in-form-data db path v)))


(rf/reg-event-db
  :master-list-filter-change
  (fn [db [_ v]]
    (s/assoc-in-form-state db :master-list-filter v)))

(rf/reg-event-db
  :form-data-pre-delete
  (fn [db [_ b]]
    (s/assoc-in-form-state db :form-data-pre-delete b)))

(rf/reg-event-db
  :wrapper-click-update
  (fn [db [_ update-fn]]
    (s/update-in-form-state db :wrapper-click update-fn)))

(rf/reg-event-db
  :set-current-screen-size
  (fn [db [_ width height]]
    (update db :window-state
            assoc :window-width width :window-height height)))

(rf/reg-event-db
  :reset-form-data-error
  (fn [db [_ error-msgs]]
    (s/assoc-in-form-state db :errors error-msgs)))

(rf/reg-event-db
  :update-form-data-error
  (fn [db [_ error-msgs vali-path]]
    (let [vali-res (if error-msgs
                     (v/errors vali-path error-msgs)
                     nil)
          new-db (if vali-res
                   (s/assoc-in-form-state db [:errors vali-path] vali-res)
                   (let [error-map (s/get-in-form-state db :errors)
                         error-map (dissoc error-map vali-path)]
                     (s/assoc-in-form-state db [:errors] error-map)))]
      new-db
      )))

(rf/reg-event-db
  :merge-form-data-error
  (fn [db [_ error-msgs]]
    (s/update-in-form-state db :errors (fn [errors] (merge errors error-msgs)))))

(rf/reg-event-db
  :remove-form-data-error-keys
  (fn [db [_ error-keys]]
    (s/update-in-form-state db :errors (fn [err] (apply dissoc err error-keys)))))

(rf/reg-event-db
  :add-to-form-state
  (fn [db [_ m]]
    (s/update-form-state db (fn [form-state] (merge form-state m)))))


;; END RE-FRAME EFFECT HANDLERS

(rf/reg-event-db
  :common/show-details-edit-item
  (fn [db [_ show-details]]
    (ctrl/show-details-edit-item db show-details)))

(rf/reg-event-db
  :common/show-details-edit-item-and-set
  (fn [db [_ show-details? detail-item]]
    (-> db
        (ctrl/show-details-edit-item true)
        (s/assoc-in-form-config :form-data detail-item)
        (s/assoc-in-form-state :selected-item detail-item))))


(rf/reg-event-db
  :common/show-details-view-item
  (fn [db [_ show-details]]
    (ctrl/show-details-view-item db show-details)))

;; Used in invoice

(rf/reg-event-db
  :common/show-navigation
  (fn [db [_ show?]]
    (if show?
      (-> db
          (s/dissoc-in-form-state :hide-headers-and-footers)
          (s/assoc-in-form-state :sidebar-toggle-cls ""))

      (-> db
          (s/assoc-in-form-state :hide-headers-and-footers true)
          (s/assoc-in-form-state :sidebar-toggle-cls "toggled")))))

(rf/reg-event-db
  :common/hide-sidebar-if-visible
  (fn [db _]
    (s/assoc-in-form-state db :sidebar-toggle-cls "toggled")))

(rf/reg-event-db
  :common/show-sidebar-if-visible-previously
  (fn [db _]
    (s/dissoc-in-form-state db :sidebar-toggle-cls)))

(rf/reg-event-db
  :common/enable-full-width-display
  (fn [db [_ full-width?]]
    (s/assoc-in-form-state db :enable-full-width-display full-width?)))

(rf/reg-event-db
  :common/detail-only-view
  (fn [db [_ detail-only?]]
    (s/assoc-in-form-state db :detail-only-view detail-only?)))

;; Used in user

(rf/reg-event-db
  :common/add-alert
  (fn [db [_ {:keys [type timeout msg] :as alert}]]
    (if (string? msg)
      (-> db
          (s/add-alert2 alert)

          ; FIXME: We need a way to remove the error key one the user corrected any input field
          ;(s/merge-form-data-error2 {:global (into #{} msg)})
          )

      db)))

(rf/reg-event-db
  :common/add-info-alert
  (fn [db [_ msg]]
    (if (string? msg)
      (s/add-info-alert-in db msg)
      db)))

(rf/reg-event-db
  :common/remove-alert-by-id
  (fn [db [_ alert-id]]
    (s/remove-alert db alert-id)))

(rf/reg-event-db
  :common/remove-all-alerts
  (fn [db [_ alert-id]]
    (s/remove-all-alerts db)))

(rf/reg-event-db
  :reduce-alert-timeouts
  (fn [db [_ amount]]
    (s/reduce-alert-timeouts db amount)))

(rf/reg-event-fx
  :common/toggle-sidebar
  (fn [{db :db} [_ notify-server?]]
    (let [cls (s/get-in-view-state db :sidebar-toggle-cls)
          new-cls (if (= cls "toggled") "" "toggled")
          new-db (s/assoc-in-view-state db :sidebar-toggle-cls new-cls)
          result {:db new-db}
          result (if notify-server?
                   (assoc result :management-api-fn [:push-view-settings {:view-state (:view-state new-db)}])
                   result)]
      result)))

(rf/reg-event-fx
  :common/toggle-sidebar-size
  (fn [{db :db} _]
    (let [cls (s/get-in-view-state db :sidebar-size-cls)
          new-cls (if (= cls "sidebar-small") "sidebar-wide" "sidebar-small")
          new-db (s/assoc-in-view-state db :sidebar-size-cls new-cls)]

      {:db new-db
       :management-api-fn [:push-view-settings {:view-state (:view-state new-db)}]
       })))

(rf/reg-event-db
  :common/toggle-sidebar-sm
  (fn [db _]
    (let [window-state (:window-state db)
          new-db (if (< (:window-width window-state) (:sm site/media-breakpoints))
                       (let [cls (s/get-in-view-state db :sidebar-toggle-cls)
                             new-cls (if (= cls "toggled") "" "toggled")
                             new-db (s/assoc-in-view-state db :sidebar-toggle-cls new-cls)]
                         new-db)
                       db)]

      new-db)))

(rf/reg-event-fx
  :common/toggle-navbar-collapse
  (fn [{db :db} [_ notify-server?]]
    (let [cls (s/get-in-form-state db :navbar-toggle-cls)
          new-cls (if (= cls "toggled") "" "toggled")
          new-db (s/assoc-in-form-state db :navbar-toggle-cls new-cls)
          result {:db new-db}]
      result)))

(rf/reg-event-db
  :common/remove-open-document-flag
  (fn [db _]
    (s/dissoc-in-form-state db :open-document)))


;;; CRUD OPS

(rf/reg-event-fx
  :crud-ops/get
  (fn [cofx [_ { type :type record-with-id :record }]]
    {
     :db (:db cofx)
     :crud-ops-api-fn [:crud-ops-api/get {:type type
                                          :op :get
                                          :record record-with-id}]}))

(rf/reg-event-fx
  :crud-ops/save
  (fn [cofx [_ { type :type record-with-id :record next-fn :next-fn }]]
    {
     :db (:db cofx)
     :crud-ops-api-fn [:crud-ops-api/save {:type type
                                           :op :save
                                           :record record-with-id
                                           :next-fn next-fn}]}))

(rf/reg-event-fx
  :crud-ops/remove
  (fn [cofx [_ { type :type record-with-id :record next-fn :next-fn }]]
    {
     :db (:db cofx)
     :crud-ops-api-fn [:crud-ops-api/remove {:type type
                                             :op :remove
                                             :record-with-id record-with-id
                                             :next-fn next-fn}]}))

(rf/reg-event-fx
  :crud-ops/switch-project
  (fn [cofx [_ { type :type record-with-id :record project :project}]]
    {
     :db (:db cofx)
     :crud-ops-api-fn [:crud-ops-api/switch-project {:type type
                                                     :record-with-id record-with-id
                                                     :project project}]}))


(rf/reg-event-fx
  :chat/send-message
  (fn [{db :db} [_ message]]
    (let [form-state (:form-state db)
          form-name (:form-name form-state)
          chat-room-id (s/get-in-form-data db :_id)]
      {
       :db          db
       :chat-api-fn [:chat-api/send-message (merge message
                                                   {:chat-room-id chat-room-id})]
       })))

(rf/reg-event-fx
  :chat/remove-message
  (fn [{db :db} [_ message-id]]
    {
     :db          db
     :chat-api-fn [:chat-api/remove-message {:message-id message-id}]
     }))

(rf/reg-event-fx
  :chat/load-messages
  (fn [{db :db} [_ top-msg-stub]]
    (if-not (= (get-in db [:chat-room-msg-cache (:chat-room-id top-msg-stub) :status]) :all-loaded)
      {
       :db          (assoc-in db [:chat-room-msg-cache (:chat-room-id top-msg-stub) :status] :loading)
       :chat-api-fn [:chat-api/load-messages top-msg-stub]
       }

      {:db db})))

;; comment events

(rf/reg-event-fx
  :related-comments/send-comment
  (fn [{db :db} [_ comment]]
    (let [form-state (:form-state db)
          form-name (:form-name form-state)]
      {
       :db          db
       :comment-api-fn [:comment-api/send-comment (merge comment
                                                   {:related-type form-name
                                                    :related-id (s/get-in-form-data db :_id)})]
       })))

(rf/reg-event-fx
  :related-comments/remove-comment
  (fn [{db :db} [_ comment-id]]
    {
     :db          db
     :comment-api-fn [:comment-api/remove-comment {:comment-id comment-id}]
     }))


;;; Success events

(rf/reg-event-db
  :crud-ops/get-success
  (fn [db [_ {type :type record-with-id :record}]]
    (let [master-data (crud-ops/add-or-replace-record-in (s/master-data db) record-with-id)
          form-state (assoc (s/form-state db) :selected-item record-with-id)
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data))
          new-db (ctrl/show-details-view-item new-db true)]

      new-db)))


(rf/reg-event-db
  :crud-ops/save-success
  (fn [db [_ {type :type record-with-id :record}]]
    (let [master-data (crud-ops/add-or-replace-record-in (s/master-data db) record-with-id)
          form-state (assoc (s/form-state db) :selected-item record-with-id)
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data))
          new-db (ctrl/show-details-view-item new-db false)]

      new-db)))

(rf/reg-event-db
  :crud-ops/remove-success
  (fn [db [_ {type :type record-with-id :record}]]
    (let [master-data (crud-ops/remove-listed-record-in (s/master-data db) record-with-id)
          form-state (dissoc (s/form-state db) :selected-item)
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data))]
      new-db)))


(rf/reg-event-db
  :crud-ops/switch-project-success
  (fn [db [_ {:keys [result is-visible]}]]
    (let [master-data (if is-visible
                        (crud-ops/add-or-replace-record-in (s/master-data db) result)
                        (crud-ops/remove-listed-record-in (s/master-data db) result))
          form-state (if is-visible
                       (assoc (s/form-state db) :selected-item result)
                       (dissoc (s/form-state db) :selected-item))
          new-db (-> db
                     (s/assoc-form-state form-state)
                     (s/assoc-master-data master-data))]
      new-db)))


;; INIT success events

(rf/reg-event-fx
  :knowledgebase/load-list-success
  (fn [{db :db} [_ kb-list]]
    (let [new-db (-> db
                     (s/assoc-master-data kb-list)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item kb-list)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
      )))

(rf/reg-event-fx
  :timeroll/load-list-success
  (fn [{db :db} [_ tr-list]]
    (let [new-db (-> db
                     (s/assoc-master-data tr-list)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item tr-list)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
      )))

(rf/reg-event-fx
  :addressbook/load-list-success
  (fn [{db :db} [_ ab-list]]
    (let [new-db (-> db
                     (s/assoc-master-data ab-list)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item ab-list)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
      )))

(rf/reg-event-fx
  :chat-room/load-list-success
  (fn [{db :db} [_ chat-rooms]]
    (let [new-db (-> db
                     (s/assoc-master-data chat-rooms)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item chat-rooms)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
      )))

(rf/reg-event-fx
  :meetings/load-list-success
  (fn [{db :db} [_ meetings]]
    (let [new-db (-> db
                     (s/assoc-master-data meetings)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item meetings)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
      )))

(rf/reg-event-fx
  :documents/load-list-success
  (fn [{db :db} [_ doc-list]]
    (let [new-db (-> db
                     (s/assoc-master-data doc-list)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item doc-list)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
      )))

(rf/reg-event-fx
  :invoices/load-list-success
  (fn [{db :db} [_ invoices-list]]
    (let [new-db (-> db
                     (s/assoc-master-data invoices-list)
                     (s/assoc-in-form-state :is-loading false)
                     (s/assoc-in-form-state :selected-item (c/find-selected-item invoices-list)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]
      {:db new-db}
      )))


(rf/reg-event-db
  :session-field-change
  (fn [db [_ path v]]
    (s/assoc-in-session db path v)))

(rf/reg-event-db
  :session-field-remove
  (fn [db [_ path]]
    (s/dissoc-in-session db path)))
