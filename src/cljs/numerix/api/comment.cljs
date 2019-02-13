(ns numerix.api.comment
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.lib.messages :refer [push-msg-handler]]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cljs.core.async :as a]
            [taoensso.encore :as enc])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))


;; Push answer from server
(defmethod push-msg-handler :related-comments/new-comment
  [[_ comment]]
  ;(log/info ":related-comments/new-comment " comment)
  (rf/dispatch [:related-comments/new-comment-from-server comment]))


(defmethod push-msg-handler :related-comments/removed-comment
  [[_ comment-stub]]
  (rf/dispatch [:related-comments/removed-comment-from-server comment-stub]))


(defn load-comments [related-info success-fn]
  (socket/send! [:related-comments/load-comments (merge related-info
                                                 {:max-messages 1000})] 5000
                (fn [response]
                  ;(log/info "load chat messages " (pr-str response))
                  (success-fn (last response)))))


(defn send-comment [comment]
  (socket/chsk-send! [:related-comments/new-comment comment]))

(defn remove-comment [message-id]
  (socket/chsk-send! [:related-comments/remove-comment message-id]))
