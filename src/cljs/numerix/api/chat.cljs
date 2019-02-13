(ns numerix.api.chat
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.lib.messages :refer [push-msg-handler]]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cljs.core.async :as a]
            [taoensso.encore :as enc])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

;; Push answer from server
(defmethod push-msg-handler :chat/new-chat-msg
  [[_ message]]
  ;(log/info ":chat/new-chat-msg " message)
  (rf/dispatch [:chat/new-chat-msg-from-server message]))

(defmethod push-msg-handler :chat/removed-chat-msg
  [[_ message-stub]]
  (rf/dispatch [:chat/removed-msg-from-server message-stub]))

(defmethod push-msg-handler :presence/presence-update
  [[_ presence]]
  ;(log/info ":presence/presence-update " presence)
  (rf/dispatch [:presence/remote-presence-update presence]))


(defmethod push-msg-handler :presence/user-is-online
  [[_ presence-or-lst]]
  ;(log/info ":presence/user-is-online " presence)
  (if (sequential? presence-or-lst)
    (doseq [presence presence-or-lst]
      (rf/dispatch [:presence/user-is-online presence]))

    (rf/dispatch [:presence/user-is-online presence-or-lst])))

(defmethod push-msg-handler :presence/user-is-offline
  [[_ presence-or-lst]]
  ;(log/info ":presence/user-is-offline " presence)
  (if (sequential? presence-or-lst)
    (doseq [presence presence-or-lst]
      (rf/dispatch [:presence/user-is-offline presence]))

    (rf/dispatch [:presence/user-is-offline presence-or-lst])))


(defn list-chat-rooms [max success-fn]
  (socket/send! [:chat-room/list max] 5000
                (fn [response]
                  ;(log/info "list chat rooms result " (pr-str response))
                  (success-fn (last response)))))



(defn load-chat-messages [room-info success-fn]
  (socket/send! [:chat/load-chat-messages (merge room-info
                                                 {:max-messages 10})] 5000
                (fn [response]
                  ;(log/info "load chat messages " (pr-str response))
                  (success-fn (last response)))))


(defn send-msg [message]
  (socket/chsk-send! [:chat/new-message message]))

(defn remove-msg [message-id]
  (socket/chsk-send! [:chat/remove-message message-id]))


(defn log-user-presence [data]
  (socket/chsk-send! [:presence/log-user-presence data]))

