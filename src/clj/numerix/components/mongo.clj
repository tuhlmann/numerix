(ns numerix.components.mongo
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as timbre]
            [monger.core :as mg]))

(timbre/refer-timbre)

(defrecord Mongo [uri db]
  component/Lifecycle

  (start [component]
    (if uri
      (let [{:keys [conn db]} (mg/connect-via-uri uri)]
        (info "Connect via uri " uri ": " (pr-str db))
        (assoc component
          :connection conn
          :db db))
      (let [conn (mg/connect)]
        (info "Connect via connect " (pr-str conn))
        (assoc component
          :connection conn
          :db (mg/get-db conn "mongo-dev")))))

  (stop [component]
    (when-let [conn (:connection component)]
      (info "Disconnecting database")
      (mg/disconnect conn))
    (assoc component
      :connection nil
      :db nil)))

(defn new-mongo-db
  ([]
   (map->Mongo {}))
  ([uri]
   (map->Mongo {:uri uri})))