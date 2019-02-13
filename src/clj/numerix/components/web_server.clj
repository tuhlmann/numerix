(ns numerix.components.web-server
  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre            :as timbre]
    [org.httpkit.server         :refer [run-server]]
    [numerix.handler            :refer [the-application]]
    [numerix.site               :as site]
    [numerix.components.ws      :as ws]))

(timbre/refer-timbre)

(defrecord HttpServer [port is-dev? ws-connection server-stop]
  component/Lifecycle

  (start [component]
    (if server-stop
      component
      (let [component (component/stop component)

            {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (ws/ring-handlers ws-connection)

            handler (site/all-routes ajax-post-fn ajax-get-or-ws-handshake-fn)

            server-stop (run-server (the-application handler is-dev?) {:port port})]
        (info "HTTP server started")
        (assoc component :server-stop server-stop))))

  (stop [component]
    (when server-stop (server-stop))
    (info "HTTP server stopped")
    (assoc component :server-stop nil)))


;; Constructor function
(defn new-http-server [port is-dev?]
  (map->HttpServer {:port port, :is-dev? is-dev?}))
