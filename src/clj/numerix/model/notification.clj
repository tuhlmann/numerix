(ns numerix.model.notification
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.model.user :as db-user]
            [numerix.model.tag :as tags]
            [numerix.service.html-cleaner :as html-cleaner]
            [numerix.model.project :as prj]
            [numerix.config :refer [C]]
            [numerix.lib.helpers :as h]
            [numerix.model.membership :as member]
            [numerix.model.user :as user]
            [numerix.model.user-db :as user-db]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth]
            [numerix.model.chat-db :as chat-db]
            [taoensso.encore :as enc]
            [numerix.components.ws :as ws]
            [numerix.model.presence :as presence]
            [clj-time.core :as t]
            [mongoruff.collection :as ruff]
            [monger.query :as mq]
            [numerix.components.mailer :as mailer]))

(def notification-coll (:notification db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db notification-coll)
      (mc/create db notification-coll {})
      (mc/ensure-index db notification-coll (array-map :user-id 1) { :unique false })
      (mc/ensure-index db notification-coll (array-map :project-id 1) { :unique false })
      (mc/ensure-index db notification-coll (array-map :unread 1) { :unique false }))))

(defn resolve-notification-data [notification]
  (case (str (:type notification))
    "chat"
    (if-let [c (chat-db/get-chat-message (:chat-msg-id notification))]
      (assoc notification :__message (:message c))

      notification)

    notification))


(def notify-schema
  {
   :db (fn[] (db/get-db))
   :collection notification-coll
   :update-keys h/list-keys-without-augments
   :after-read (fn [rec] (resolve-notification-data rec))
   }
  )

;; Low level, DB access functions

(defn get-notification [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db notification-coll id)))

(defn list-notifications
  ([user]
   (list-notifications user 20 nil))
  ([user limit last-loaded-ts]
   (let [qry {:user-id (:_id user)
              :project-id (:current-project user)}
         qry (if last-loaded-ts
               (merge qry
                      {:ts {$lt last-loaded-ts}})
               qry)]

     (ruff/query-many
        notify-schema
        (fn [db coll]
          (let [sorted (mq/with-collection db coll
                                           (mq/find qry)
                                           ;; it is VERY IMPORTANT to use array maps with sort
                                           (mq/sort (array-map :ts -1))
                                           (mq/limit limit))]
            (into [] sorted)))))))


(defn update-notification [notification]
  (ruff/update notify-schema notification))


(defn remove-all-notifications [project-id user-id]
  (let [db (db/get-db)]
    (mc/remove db notification-coll {:project-id project-id
                                     :user-id user-id})))


(defn notify-chat-message [notify-data chat-message calling-user called-member presences]
  ;(log/info "create notification " (pr-str notify-data))
  (enc/when-let [notification (update-notification notify-data)
                 _ called-member]

    (log/info "send notification to " (pr-str presences))

    (when (= (:status notification) :unread)
      (mailer/send-chat-notification (C :mailer) notification calling-user called-member chat-message))
    (ws/send-presence-list!
      (C :ws-connection)
      presences
      [:notification/new-notification notification])))



(defn flatten-group-mention [members mention]
  (case (:group-name mention)

    "team"
    (map (fn [member]
           {:mention-type :person
            :user-id (:user-id member)}) members)

    nil))



(defmethod event-msg-handler :notification/mark-as-read
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (enc/when-let
    [user-id (config/get-auth-user-id-from-req ring-req)
     connected-uids (:any @(:connected-uids (C :ws-connection)))
     presences-of-user (presence/list-connected-by-user-id user-id connected-uids)

     notification (get-notification ?data)
     upd-notifi (update-notification (assoc notification :status :read))]

    (ws/send-presence-list!
      (C :ws-connection)
      (map :presence-id presences-of-user)
      [:notification/updated-notification upd-notifi])))

(defmethod event-msg-handler :notification/remove-all-notifications
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info ":notification/remove-all-notifications")
  (enc/when-let
    [user (user-db/get-user-by-req ring-req)
     connected-uids (:any @(:connected-uids (C :ws-connection)))
     presences-of-user (presence/list-connected-by-user-id (:_id user) connected-uids)

     _ (remove-all-notifications (:current-project user) (:_id user))
     ;list-notifications user-with-prj)
    ]

    (log/info "send new notification list")
    (ws/send-presence-list!
      (C :ws-connection)
      (map :presence-id presences-of-user)
      [:notification/remove-all-notifications])))

