(ns numerix.lib.messages)

(defmulti event-msg-handler :id) ; Dispatch on event-id

; Dispatch on user event key
#?(:cljs
   (defmulti push-msg-handler (fn [[id _]] id)))

;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))

