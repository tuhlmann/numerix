(ns numerix.model.ext-session
  (:require [numerix.db :as db]
            [numerix.config :as cfg]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.joda-time]
            [clj-time.core :as t]
            [clojure.pprint :as pprint]
            [numerix.auth-utils :as autil]
            [numerix.model.user :as user]
            [numerix.model.user-db :as user-db]
            [numerix.lib.helpers :as h]
            [taoensso.timbre :as log])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]
           [java.util UUID]))

(def ext-session (:ext-session db/coll))

(def cookie-name "ring-extsess-id")

(defn- create-ext-cookie [cookie-id expires]
  {:path "/"
   :value cookie-id
   :expires expires
   :http-only true
   :secure (cfg/production-mode?)
   })

(defn- delete-ext-cookie []
  {:value "kill", :max-age 1})

(defn delete-ext-session [uid]
  (let [db (db/get-db)
        _ (when uid
            (mc/remove db ext-session {:userId uid}))]
    (delete-ext-cookie)))  
  

(defn create-ext-session [uid]
  (let [db (db/get-db)
        _ (delete-ext-session uid)
        cookie-id (.toString (UUID/randomUUID))
        expires-date (h/calc-expires-date h/cookie-expires-days)
        rec (mc/insert-and-return db ext-session {:_id cookie-id, :userId uid, :expires expires-date})]
    (create-ext-cookie cookie-id expires-date)))  
  
(defn- get-ext-session-by-cookie-id [cookie-id]
  (when-let [db (db/get-db)]
    (if cookie-id
      (mc/find-map-by-id db ext-session cookie-id)
      nil)))

(defn- find-user-by-ext-session [ext-session]
  (if (t/before? (t/now) (:expires ext-session))
    (user-db/get-user (:userId ext-session))
    
    nil))

;;; API

(defn handle-ext-session-flag [response uid remember-me]
  (if remember-me
    (-> 
      response 
      (update-in [:cookies cookie-name] (fn [_] (create-ext-session uid))))
    
    response))

;; FIXME Once the user session has been authenticated this must not run again. So, we need to detect whether
;; we have already been authenticated.
(defn wrap-extended-session
  "Add this middleware after :cookies have been added to the request map.
   It will check for the ext session token and log the user in if no :identity is in the session."
  [handler]
  (fn [request]
    (if (nil? (get-in request [:session :identity])) 
      (if-let [cookie-id (get-in request [:cookies cookie-name :value])]
        (if-let [ext-session (get-ext-session-by-cookie-id cookie-id)]
          (if-let [user (find-user-by-ext-session ext-session)]
            (do
              ;; Log user in
              ;(println "Found user " user)
              (let [response (handler
                             (assoc request :session (autil/add-auth-info (get request :session {}) user)))]
                (assoc response :session (autil/add-auth-info {} user))))
          
            (do
              ;;(println "No User found for extsession " ext-session)
              (let [response (handler request)]
                (-> response
                    (update-in [:cookies cookie-name] (fn [_] (delete-ext-session (:userId ext-session))))))))
                
          (do
            ;;(println "No ext session found, remove cookie")
            (let [response (handler request)]
              (-> response
                  (update-in [:cookies cookie-name] (fn [_] (delete-ext-cookie)))))))
      
        (do
          ;;(println "No cookie id found")
          (handler request)))
      
      (do
        ;(println "Found existing identity. Do not process auth token")
        (handler request)))    
  ))


(defn maybe-init  
  "If it doesn't exist yet, it creates a collection.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db ext-session)
      (mc/create db ext-session {})
      (mc/ensure-index db ext-session (array-map :userId 1) { :unique true } ))))
  