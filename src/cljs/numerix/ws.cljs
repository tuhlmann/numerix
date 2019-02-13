(ns numerix.ws
  (:require [taoensso.sente :as sente]
            [taoensso.timbre :as log]
            [cognitect.transit :as transit]
            [numerix.lib.datatypes :refer [ObjectId]]
            [taoensso.sente.packers.transit :as sente-transit]
            [cljs-time.format :refer [formatter formatters unparse parse]]
            [numerix.state :as s]
            [numerix.lib.messages :as msg :refer [event-msg-handler push-msg-handler]]
            [re-frame.core :as rf]
            [taoensso.encore :as enc]))

(sente/set-logging-level! :info) ; :info for less, :debug for more

(defonce handlers (atom {}))

(defn register-handler [key callback]
  (swap! handlers (fn [handler-map]
                   (update-in handler-map [key] #(into[] (conj % callback))))))

(defn call-handler [key ?data]
  (doseq [cb (get @handlers key)]
    (cb ?data)))

;(defmulti push-msg-handler (fn [[id _]] id))       ; Dispatch on user event key

(defmethod push-msg-handler :chsk/ws-ping
  [_]
  (log/info "received :chsk/ws-ping"))


(defmethod push-msg-handler :numerix/testevent
  [[_ event]]
  (log/info "numerix/testevent received from server: " event))

;(defmethod push-msg-handler :default ; Fallback
;  [[data event]]
;  (js/console.log "Unhandled User event: " data event))

(defmethod push-msg-handler :default ; Fallback
  [data]
  (js/console.log "Fallback, Unhandled User event: " data))


;;;(defmulti event-msg-handler :id) ; Dispatch on event-id
; Wrap for logging, catching, etc.:
;(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
;  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event]}]
  (js/console.log "Unhandled event: %s" (pr-str event)))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (:first-open? ?data false)
    (log/debug "Channel socket successfully established!")
    (log/debug "Channel socket state change: " (pr-str ?data))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  #_(if (:first-open? ?data false)
     (log/info "handshake Channel socket successfully established!")
     (log/info "handshake Channel socket state change: " (pr-str ?data)))
  (call-handler :chsk/handshake ?data)) ;call list of handlers


(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data event]}]
  ;(js/console.log "Event from server: %s" (pr-str ev-msg))
  (log/debug "Push event from server: " ?data)
  ;(push-msg-handler {:id (first ?data), :event (second ?data)})
  (push-msg-handler ?data))


;;;; TEST
;(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
;  ;(js/console.log "Event: %s" (pr-str event))
;  ;(js/console.log "event-msg-handler received: " ev-msg)
;  (event-msg-handler ev-msg))


;;; when using :edn format packer
;(defn read-object-id [id]
;  (if (sequential? id)
;    (case (str (first id))
;      "org.bson.types.ObjectId" {:$oid (str (last id))}
;      (str (last id)))
;  (str id)))

;(cljs.reader/register-tag-parser! 'object read-object-id)

(def objectid-write-handler
  (transit/write-handler
    (constantly "oid") (fn [v] (.toString v))))

(def objectid-read-handler
  (transit/read-handler (fn [r] (ObjectId. r))))

(def lossless-date-formatter (formatter "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"))

(def date-time-write-handler
  (transit/write-handler
    (constantly "date-time")
    (fn [v]
      (let [re (unparse lossless-date-formatter v)]
        re))))

(def date-time-read-handler
  (transit/read-handler
    (fn [r]
      (let [re (parse lossless-date-formatter r)]
        re))))

;(def packer :edn)
; this=> (def packer (sente-transit/get-flexi-packer :edn))
;(def packer (sente-transit/get-flexi-packer :json))
(def packer (sente-transit/->TransitPacker :json
                                           {:handlers
                                            {numerix.lib.datatypes/ObjectId objectid-write-handler
                                             goog.date.UtcDateTime date-time-write-handler
                                             goog.date.DateTime date-time-write-handler}}

                                           {:handlers
                                            {"oid" objectid-read-handler
                                             "date-time" date-time-read-handler}}))

(defn init-connection []
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk" {:type :auto :packer packer})]

    (def chsk chsk)
    (def ch-chsk ch-recv)     ; ChannelSocket's receive channel
    (def chsk-send! send-fn)  ; ChannelSocket's send API fn
    (def chsk-state state))   ; Watchable, read-only atom

  (defonce router (sente/start-chsk-router! ch-chsk msg/event-msg-handler*)))

(defn response-ok [data]
  ;(log/info "Code is " (= (:code data) :ok))
  (= (:code data) :ok))

(defn wrapped-send-msg [result type]
  (if (sequential? result)
    (let [[_ data] result]
      (when (:msg data)
        (rf/dispatch [:common/add-alert {:type type :msg (:msg data) :timeout (:timeout data 10)}])))

    (log/error "Got wrong server response " (pr-str result))))


(defn send! [event & [?timeout-ms ?cb-fn]]
  ;(log/info "Event is " (pr-str event))
  (let [cb (fn [result]
             ;(log/info "Result " (pr-str result))
             (if (and
                   (sente/cb-success? result)
                   ;(not= result :chsk/closed)
                   ;(sequential? result)
                   (response-ok (last result)))
               (do
                 (wrapped-send-msg result :info)
                 (when ?cb-fn
                   (?cb-fn result)))
               (do
                 (wrapped-send-msg result :error)
                 (when ?cb-fn
                   (?cb-fn result)))))]
    (chsk-send! event ?timeout-ms cb)))

