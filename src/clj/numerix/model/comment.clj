(ns numerix.model.comment
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
            [numerix.model.comment-db :as comment-db]))


(defmethod event-msg-handler :related-comments/load-comments
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     membership (member/get-membership-by-user user)
     related-id (:related-id ?data)
     related-type (:related-type ?data)
     has-access? (form-auth/as-form-read? related-type membership)
     comments (let [lst (comment-db/list-comments related-id (:max-messages ?data) (:last-message-ts ?data))]
                     {:count (count lst)
                      :items lst})]

    [:related-comments/load-comments { :code :ok, :data { :result comments }}]
    [:related-comments/load-comments { :code :error :msg "Error listing chat messages"} ]))


(defmethod event-msg-handler :related-comments/new-comment
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (enc/when-let
    [ws-connection (C :ws-connection)
     connected-uids (:any @(:connected-uids (C :ws-connection)))
     user (user-db/get-user-by-req ring-req)
     membership (member/get-membership-by-user user)
     comment (merge ?data {:ts (t/now)
                           :author-id (:_id user)
                           })
     ;chat-room (chat-db/get-chat-room (:chat-room-id chat-msg))

     ;; check that user has edit-permission for the given form item (meeting, etc.)
     has-access? (form-auth/as-form-edit? (:related-type comment) membership (:_id user) comment nil)

     connected-identities (subscription/list-subscribers
                            (:current-project user)
                            (:related-type comment)
                            (:related-id comment))

     filtered (filterv #(contains? connected-uids (:presence-id %)) connected-identities)

     page-presences (presence/list-connected-by-page-and-item
                      connected-uids
                      (:related-type comment)
                      (:related-id comment))

     project-presences (presence/list-connected-by-project (:current-project user) connected-uids)

     members-with-access (member/list-active-memberships-for-project (:current-project user))
     members-with-access (member/add-user-name-and-presence members-with-access)

     comment (if (seq (:mentions comment))
                (let [mentions (filterv (fn [mention]
                                          (if (= (:mention-type mention) :person)
                                            (some #(= (:user-id mention) (:user-id %)) members-with-access)

                                            true)) (:mentions comment))]
                  (assoc comment :mentions mentions))

                comment)

     upd-comment (comment-db/update-comment comment)

     ]

    ;(log/info "mentions " (:mentions upd-comment))

    (enc/when-let
      [flat-mentions
       (reduce
         (fn [akku mention]
           (case (:mention-type mention)

             :group
             (concat akku (notification/flatten-group-mention members-with-access mention))

             (conj akku mention)))

         nil (:mentions upd-comment))
       ;; don't send notification to myself
       flat-mentions (filterv #(not= (:user-id %) (:_id user)) flat-mentions)]

      (doseq [mention flat-mentions]
        (let [presences (filter #(= (:user-id mention) (:user-id %)) project-presences)
              called-member (first (filter #(= (:user-id mention) (:user-id %)) members-with-access))
              n {:user-id (:user-id mention)
                 :project-id (:current-project user)
                 :type :comment
                 :comment-id (:_id upd-comment)
                 :related-id (:related-id upd-comment)
                 :related-type (:related-type upd-comment)
                 :__message (:message upd-comment) ;; FIXME: Need to truncate long messages!
                 :author-id (:_id user)
                 :author-name (h/name-or-email user)
                 :__author-profile-img-src (h/profile-img-src (:profile-image-id user) (:email user))
                 :status (if (some #(= (:user-id mention) (:user-id %)) page-presences)
                           :read
                           :unread)
                 }]
          (notification/notify-chat-message
            n
            upd-comment
            user
            called-member
            (map :presence-id presences)))))

    ;(log/info "connected identities " (pr-str connected-identities) " , " connected-uids)
    ;(log/info "message is " upd-chat-msg)
    (ws/send-presence-list!
      ws-connection
      (map :presence-id filtered)
      [:related-comments/new-comment upd-comment])
    ))

(defmethod event-msg-handler :related-comments/remove-comment
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (enc/when-let
    [ws-connection (C :ws-connection)
     connected-uids (:any @(:connected-uids (C :ws-connection)))
     user (user-db/get-user-by-req ring-req)
     membership (member/get-membership-by-user user)
     ;; check that user has edit-permission for the given form item (meeting, etc.)

     comment-id ?data
     comment (comment-db/get-comment comment-id)
     has-access? (form-auth/as-form-remove? (:related-type comment) membership (:_id user) comment)
     upd-comment (comment-db/remove-comment comment)


     connected-identities (subscription/list-subscribers
                            (:current-project user)
                            (:related-type comment)
                            (:related-id comment))

     filtered (filterv #(contains? connected-uids (:presence-id %)) connected-identities)]

    (ws/send-presence-list!
      ws-connection
      (map :presence-id filtered)
      [:related-comments/removed-comment {:_id comment-id
                                          :related-type (:related-type comment)
                                          :related-id (:related-id comment)}])
    ))
