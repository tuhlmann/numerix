(ns numerix.config
  (:require [environ.core :refer [env]]
            [dragonmark.util.props :as props]
            [taoensso.timbre :as log]
            [ring.middleware.anti-forgery]
            [clojure.string :as string])
  (:import (org.bson.types ObjectId)))

(defn http-port []
  (or (env :http-port) (get-in @props/info [:http :port]) "10555"))

(defn get-system-config []
  (log/info "We start in run mode " (name @props/run-mode)
            ", production is set to " (props/production-mode?)
            ", http port is " (http-port)
            ", dev? is " (= (env :dev?) "true"))
  {
   :port (Integer/parseInt (http-port))
   :is-dev? (or (= (env :dev?) "true") false)
  })

(defn production-mode? []
  (props/production-mode?))

(defn dev-mode? []
  (props/dev-mode?))

(defn log-path []
  (:log-path @props/info))

(defn log-file []
  (let [path (or (log-path) "/var/log/numerix")]
    (str path "/numerix.log")))

;; System State

(defonce app-state (atom {}))

(defn C
  "Returns a named component from the current app state"
  ([component]
   (get @app-state component))
  ([component key]
   (let [res (get-in @app-state [component key])]
     res)))


(def mongo-uri (get-in @props/info [:mongo-uri]))

(def host (get-in @props/info [:host]))

(def contact-email (get-in @props/info [:contact]))
(def email-from (get-in @props/info [:email :from]))

(def email-conn {:host (get-in @props/info [:email :host])
           :ssl (get-in @props/info [:email :ssl])
           :user (get-in @props/info [:email :user])
           :pass (get-in @props/info [:email :pass])})

(defn get-client-id-from-req [req]
  (get-in req [:params :client-id]))

(defn get-identity-from-req [req]
  (if-let [identity (get-in req [:session :identity])]
    (name identity)
    nil))

(defn get-presence-id-from-req [req]
  (str (get-identity-from-req req) "/" (get-client-id-from-req req)))

(defn make-presence-id [identity client-id]
  (str identity "/" client-id))

(defn get-identity-from-presence [presence-id]
  (if-let [pos (string/index-of presence-id "/")]
    (subs presence-id 0 pos)
    presence-id))

(defn get-auth-user-from-req [req]
  (get-in req [:session :auth-user]))

(defn get-auth-user-id-from-req [req]
  (when-let [auth-user (get-auth-user-from-req req)]
    (ObjectId. (:_id auth-user))))

(defn server-config-for-client
  "Provides a map of parameters of the current server settings
  that are save for the clients.
  Everything in this map is seen by the client!"
  []
  {:run-mode @props/run-mode
   :production-mode? (production-mode?)
   :dev-mode? (dev-mode?)
   :host host
   :csrf-token "xy" ;ring.middleware.anti-forgery/*anti-forgery-token*
   })
