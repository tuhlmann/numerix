(ns numerix.components.presence-observer
  (:require
    [taoensso.timbre :as log]
    [numerix.config :as cfg]
    [clojure.pprint :as pprint]
    [clojure.core.async :as a :refer [>! <! >!! <!! put! go go-loop chan buffer close! thread alts! alts!! timeout sliding-buffer]]
    [com.stuartsierra.component :as component]
    [numerix.lib.helpers :as h]
    [numerix.model.presence :as presence]
    [numerix.model.subscription :as subscription]
    [clj-time.core :as t]))

(def presence-observer-sleep 60000)
(def presence-observer-max-age (t/minutes 5))
(def presence-observer-expiring-wait (t/minutes 3))

(defn- observe-connection-change [ws-connection]
  (when-let [connected-uids (:connected-uids ws-connection)]
    (add-watch
      connected-uids
      :presence-observer
      (fn [_ _ old-state new-state]
        (when (not= old-state new-state)
          (presence/connection-state-change (:any old-state) (:any new-state)))))))


(defn- remove-connection-observer [ws-connection]
  (when-let [connected-uids (:connected-uids ws-connection)]
    (remove-watch connected-uids :presence-observer)))


(defrecord PresenceObserver [presence-channel ws-connection]
  component/Lifecycle

  (start [component]
    (if presence-channel
      component

      (let [component (component/stop component)
            channel (chan (sliding-buffer 10))]

        (log/info "Presence Observer started")
        (observe-connection-change ws-connection)
        (go-loop []
          (when-let [[observe channel] (alts! [channel (timeout presence-observer-sleep)])]

            (log/info "presence-oberserver observes now")
            (presence/renew-connected presence-observer-expiring-wait)
            (presence/remove-expired presence-observer-max-age)
            (subscription/remove-expired)

          (recur)))

        (assoc component
          :presence-channel channel))))

  (stop [component]
    (log/info "Presence Observer stopped")
    (remove-connection-observer ws-connection)
    (when-let [channel (:presence-channel component)]
      (close! channel))
    (assoc component
      :presence-channel nil)))

;; Constructor function
(defn new-presence-observer []
  (map->PresenceObserver {}))

