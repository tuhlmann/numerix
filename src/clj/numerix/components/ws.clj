(ns numerix.components.ws
  (:require [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [taoensso.sente.server-adapters.http-kit :as sente-http]
            [taoensso.sente :as sente]
            [taoensso.sente.interfaces :refer [IPacker]]
            [taoensso.encore :as enc]
            [clojure.pprint :as pprint]
            [cognitect.transit :as transit]
            [taoensso.sente.packers.transit :as sente-transit]
            [numerix.lib.messages :refer [event-msg-handler]]
            [numerix.config :as config]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [numerix.config :refer [C]]
            [clojure.string :as string])
  (:import (org.bson.types ObjectId)
           (org.joda.time DateTime ReadableInstant)))

;; Important:
;; The Sente client-id consists of the request identity (the email address) and the unique client-id
;; assigned to one specific device (browser tab, etc.)
;; to send a message to one specific device the full ID is needed, you can also send a message to all devices
;; of the user.

(def web-server-adapter taoensso.sente.server-adapters.http-kit/http-kit-adapter)

(def ping-counts (atom 0))

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :chsk/ws-ping
  [_]
  (swap! ping-counts inc)
  (when (= 0 (mod @ping-counts 10))
    (log/debug "ping counts: " @ping-counts)))

(defmethod event-msg-handler :numerix/testevent
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "Received event")
  ;(pprint/pprint ev-msg)
  (if ?reply-fn
    (?reply-fn [:numerix/testevent {:message (str "Hello socket from server Callback, received: " ?data)}])
    (when-let [identity (get-in ring-req [:session :identity])]
      (log/info "Send Push to user " identity)
      (send-fn identity [:numerix/testevent {:message (str "Hello socket from server Event (no callback), received: " ?data)}])
      )))

;; Called by Sente if a user connects that was not previously connected.
;; Can be used to keep track of online users
(defmethod event-msg-handler :chsk/uidport-open
  [_])

;; Called by Sente if a user disconnects that was previously connected.
;; Can be used to keep track of online users
(defmethod event-msg-handler :chsk/uidport-close
  [_])

;; :sente/all-users-without-uid

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (log/warnf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))


(defrecord WSRingHandlers [ajax-post-fn ajax-get-or-ws-handshake-fn])

(def lossless-date-formatter (f/formatter "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"))

(def objectid-write-handler
  (transit/write-handler
    (constantly "oid")
    (fn [v] (-> ^ObjectId v .toString))))

(def objectid-read-handler
  (transit/read-handler
    (fn [r]
      (ObjectId. r))))

#_(def joda-time-write-handler
  (transit/write-handler
    (constantly "date-time")
    (fn [v] (-> ^ReadableInstant v .getMillis))
    (fn [v] (-> ^ReadableInstant v .getMillis .toString))))

(def joda-time-write-handler
  (transit/write-handler
    (constantly "date-time")
    (fn [v]
      (f/unparse lossless-date-formatter v))))

(def joda-time-read-handler
  (transit/read-handler
    (fn [r]
      (try
        (let [re (f/parse lossless-date-formatter r)]
          re)
        (catch Exception e (log/error e))))))


;(def packer :edn)
; this=> (def packer (sente-transit/get-flexi-packer :edn ext-edn-packer))
;(def packer (sente-transit/get-flexi-packer :json)
(def packer (sente-transit/->TransitPacker :json {:handlers
                                                  {
                                                   DateTime joda-time-write-handler
                                                   ObjectId objectid-write-handler
                                                   }}
                                           {:handlers {"oid" objectid-read-handler
                                                       "date-time" joda-time-read-handler}}))


(defrecord WSConnection [ch-recv connected-uids send-fn ring-handlers]
  component/Lifecycle

  (start [component]
    (if (and ch-recv connected-uids send-fn ring-handlers)
      component

      (let [component (component/stop component)
            {:keys [ch-recv send-fn connected-uids
                    ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (sente/make-channel-socket! web-server-adapter {
                                                            :packer     packer
                                                            ;:user-id-fn config/get-identity-from-req
                                                            :user-id-fn config/get-presence-id-from-req
                                                            })]

        (log/info "WebSocket connection started")
        (assoc component
          :ch-recv ch-recv
          :connected-uids connected-uids
          :send-fn send-fn
          :stop-the-thing (sente/start-chsk-router! ch-recv event-msg-handler*)
          :ring-handlers
          (->WSRingHandlers ajax-post-fn ajax-get-or-ws-handshake-fn)))))

  (stop [component]
    (log/info "WebSocket connection stopped")
    (when-let [stop-fn (:stop-the-thing component)]
      (stop-fn))
    (assoc component
      :ch-recv nil :connected-uids nil :send-fn nil :ring-handlers nil)))

(defn- all-user-presences [connection-list identity]
  (filter (fn [it] (string/starts-with? it (str identity "/"))) connection-list))

;(defn- filter-connected-identities [connected-uids identities]
;  (filter (fn [identity]
;            (some (partial = identity) connected-uids)) identities))

(defn send! [ws-connection identity event]
  ((:send-fn ws-connection) identity event))

;(defn broadcast-all! [ws-connection event]
;  ;(log/info "ws-connection is " (pr-str ws-connection))
;  (let [uids (:connected-uids ws-connection)]
;    (doseq [uid (:any @uids)] (send! ws-connection uid event))))

(defn send-all-user-presences!
  "Sends a message to all clients of the given identity (email)
  A sente client id is <identity>/<unique-client-id>"
  [ws-connection identity event]
  (let [connected-uids (:any @(:connected-uids ws-connection))
        user-presences (all-user-presences connected-uids identity)]
    (doseq [uid user-presences] (send! ws-connection uid event))))


;(defn send-client-list!
;  "Given a list of clients send a message to all clients that are known to sente"
;  [ws-connection clients event]
;  (let [connected-uids (:connected-uids ws-connection)
;        all-clients (:any @connected-uids)
;        send-to-clients (filter (fn [it] (some #{it} all-clients)) clients)]
;    (log/info "send to clients " clients " : " send-to-clients)
;  (doseq [uid send-to-clients]
;    (log/info "send msg to " uid)
;    (send! ws-connection uid event))))

(defn send-presence-list!
  "Given a list of clients send a message to all clients that are known to sente"
  [ws-connection identities event]
  (doseq [identity identities]
    ;(log/info "send msg to " identity)
    (send! ws-connection identity event)
    ;(send-all-user-clients! ws-connection identity event)
    ))

(defn ring-handlers [ws-connection]
  (:ring-handlers ws-connection))

;; Constructor function
(defn new-ws-connection []
  (map->WSConnection {}))

