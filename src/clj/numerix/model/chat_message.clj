(ns numerix.model.chat-message
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
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
            [taoensso.encore :as enc]
            [numerix.components.ws :as ws]
            [numerix.model.presence :as presence]
            [clj-time.core :as t]
            [mongoruff.collection :as ruff]
            [monger.query :as mq]
            [numerix.model.subscription :as subscription]
            [numerix.model.notification :as notification]
            [numerix.model.chat-db :as chat-db]))


;;; WS Communication functions

;; Chat Room handlers

(defmethod event-msg-handler :chat-room/list
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     has-access? (form-auth/as-form-read? :chat-room membership)
     chat-rooms (chat-db/list-chat-rooms user)]

    [:chat-rooms/list { :code :ok, :data { :result chat-rooms }}]
    [:chat-rooms/list { :code :error :msg "Error listing meetings"} ]))

(defmethod event-msg-handler :chat-room/save
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     membership (member/get-membership-by-user-id user-id)
     chat-room ?data
     has-access? (form-auth/as-form-edit? :chat-room membership user-id chat-room (chat-db/get-chat-room (:_id chat-room)))
     upd-chat-room (chat-db/update-chat-room chat-room)]

    [:chat-room/save { :code :ok, :data { :result upd-chat-room }}]
    [:chat-room/save { :code :error :msg "Error saving chat room"} ]))


;; Chat handlers

(defmethod event-msg-handler :chat/load-chat-messages
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     chat-room (chat-db/get-chat-room (:chat-room-id ?data))
     has-access? (form-auth/as-form-read? :chat-room membership)
     chat-messages (let [lst (chat-db/list-chat-messages (:chat-room-id ?data) (:max-messages ?data) (:last-message-ts ?data))]
                     {:count (count lst)
                      :items lst})]

    [:chat/load-chat-messages { :code :ok, :data { :result chat-messages }}]
    [:chat/load-chat-messages { :code :error :msg "Error listing chat messages"} ]))


(defmethod event-msg-handler :chat/new-message
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (enc/when-let
    [ws-connection (C :ws-connection)
     connected-uids (:any @(:connected-uids (C :ws-connection)))
     user (user-db/get-user-by-req ring-req)
     membership (member/get-membership-by-user user)
     chat-msg (merge ?data {:ts (t/now)
                            :author-id (:_id user)
                            })
     chat-room (chat-db/get-chat-room (:chat-room-id chat-msg))

     ;; check that user has edit-permission for the given form item (meeting, etc.)
     has-access? (form-auth/as-form-edit? :chat-room membership (:_id user) chat-msg nil)

     connected-identities (subscription/list-subscribers
                            (:current-project user)
                            :chat-room
                            (:chat-room-id chat-msg))

     filtered (filterv #(contains? connected-uids (:presence-id %)) connected-identities)

     page-presences (presence/list-connected-by-page-and-item
                      connected-uids
                      (:related-type chat-room)
                      (:related-id chat-room))

     project-presences (presence/list-connected-by-project (:current-project user) connected-uids)

     members-with-access (member/list-active-memberships-for-project (:current-project user))
     members-with-access (member/add-user-name-and-presence members-with-access)

     chat-msg (if (seq (:mentions chat-msg))
                (let [mentions (filterv (fn [mention]
                                          (if (= (:mention-type mention) :person)
                                            (some #(= (:user-id mention) (:user-id %)) members-with-access)

                                            true)) (:mentions chat-msg))]
                  (assoc chat-msg :mentions mentions))

                chat-msg)

     upd-chat-msg (chat-db/update-chat-message chat-msg)

     ]

    ;(log/info "mentions " (:mentions upd-chat-msg))

    (enc/when-let
      [flat-mentions
       (reduce
         (fn [akku mention]
           (case (:mention-type mention)

             :group
             (concat akku (notification/flatten-group-mention members-with-access mention))

             (conj akku mention)))

         nil (:mentions upd-chat-msg))
       ;; don't send notification to myself
       flat-mentions (filterv #(not= (:user-id %) (:_id user)) flat-mentions)]

      (doseq [mention flat-mentions]
        (let [presences (filter #(= (:user-id mention) (:user-id %)) project-presences)
              called-member (first (filter #(= (:user-id mention) (:user-id %)) members-with-access))
              n {:user-id (:user-id mention)
                 :project-id (:current-project user)
                 :type :chat
                 :chat-msg-id (:_id upd-chat-msg)
                 :chat-room-id (:chat-room-id upd-chat-msg)
                 :related-type (:related-type chat-room)
                 :related-id (:related-id chat-room)
                 :__message (:message upd-chat-msg)
                 :author-id (:_id user)
                 :author-name (h/name-or-email user)
                 :__author-profile-img-src (h/profile-img-src (:profile-image-id user) (:email user))
                 :status (if (some #(= (:user-id mention) (:user-id %)) page-presences)
                           :read
                           :unread)
                 }]
          (notification/notify-chat-message
            n
            upd-chat-msg
            user
            called-member
            (map :presence-id presences)))))

    ;(log/info "connected identities " (pr-str connected-identities) " , " connected-uids)
    (log/info "updated message is " upd-chat-msg)
    (ws/send-presence-list!
      ws-connection
      (map :presence-id filtered)
      [:chat/new-chat-msg upd-chat-msg])
    ))

(defmethod event-msg-handler :chat/remove-message
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (enc/when-let
    [ws-connection (C :ws-connection)
     connected-uids (:any @(:connected-uids (C :ws-connection)))
     user (user-db/get-user-by-req ring-req)
     membership (member/get-membership-by-user user)
     ;; check that user has edit-permission for the given form item (meeting, etc.)


     message-id ?data
     chat-msg (chat-db/get-chat-message message-id)
     chat-room (chat-db/get-chat-room (:chat-room-id chat-msg))
     has-access? (form-auth/as-form-remove? :chat-room membership (:_id user) chat-msg)
     upd-chat-msg (chat-db/remove-chat-message chat-msg)


     connected-identities (subscription/list-subscribers
                            (:current-project user)
                            :chat-room
                            (:chat-room-id chat-msg))

     filtered (filterv #(contains? connected-uids (:presence-id %)) connected-identities)]

    (ws/send-presence-list!
      ws-connection
      (map :presence-id filtered)
      [:chat/removed-chat-msg {:_id message-id
                               :chat-room-id (:chat-room-id chat-msg)}])
    ))

