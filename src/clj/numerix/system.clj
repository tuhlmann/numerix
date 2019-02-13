(ns numerix.system
  (:require [com.stuartsierra.component           :as component]
            [numerix.components.ws                :as ws]
            [numerix.components.web-server        :as web-server]
            [numerix.components.mongo             :as mongo]
            [numerix.components.mailer            :as mailer]
            [numerix.components.pdf-renderer      :as pdf-renderer]
            [numerix.components.presence-observer :as presence-observer]
            [numerix.db                           :as db]
            [numerix.components.app               :as app]))

(defn component-system [config]
  (component/system-map
    :ws-connection
    (ws/new-ws-connection)

    :http-server
    (component/using (web-server/new-http-server (:port config) (:is-dev? config)) [:ws-connection])

    :database
    (mongo/new-mongo-db db/db-uri)

    :mailer
    (mailer/new-mailer)

    :presence-observer
    (component/using (presence-observer/new-presence-observer) [:ws-connection])

    :pdf-renderer
    (pdf-renderer/new-pdf-renderer)

    :app
    (component/using (app/new-app) [:ws-connection])))
