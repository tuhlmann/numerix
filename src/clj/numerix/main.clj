(ns numerix.main
  (:require
    [com.stuartsierra.component :as component]
    [clojure.tools.namespace.repl :refer [refresh refresh-all]]
    [net.cgrand.reload          :refer [auto-reload]]
    [numerix.config             :as config]
    [numerix.system             :as system]
    [taoensso.timbre            :as log]
    [numerix.db-config          :as db-config]
    [numerix.lib.roles          :as roles]
    [numerix.log-config])
  (:gen-class))

(defn add-shutdown-hook
  "Adds a handler to the system run when the JVM shuts down.
  Can be used to shut down other sub systems, like db connections or closing
  other resources."
  [the-system]
  (.addShutdownHook
    (Runtime/getRuntime)
    (Thread.
      (fn []
        (println "Running Shutdown Hook")
        (component/stop the-system)))))


;; Reloadable system

(defn init-system []
  (reset! config/app-state (system/component-system (config/get-system-config))))

(defn start []
  (log/info "start system")
  (swap! config/app-state component/start))

(defn stop []
  (swap! config/app-state (fn [s] (when s (component/stop s)))))

(defn run-application []
  (log/set-config! (numerix.log-config/numerix-log-config))
  (init-system)
  (start)
  (db-config/initialize-db)
  (roles/initialize-roles))

(defn reset []
  (stop)
  (refresh :after 'numerix.main/run-application))

(defn -main [& args]
  (log/info "Starting NUMERIX")
  (run-application)
  (add-shutdown-hook @config/app-state)
  (log/info "Numerix started"))
