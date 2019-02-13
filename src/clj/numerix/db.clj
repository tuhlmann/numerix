(ns numerix.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [numerix.config :as config])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]))

;(def ^:dynamic
;     ^clojure.lang.PersistentArrayMap
;     *connections*
;     {})

;(def db-name "numerix")
(def db-uri config/mongo-uri)

(def coll {
           :user            "users"
           :view-settings   "view-settings"
           :contact         "contacts"
           :invoice         "invoices"
           :invoice-details "invoice-details"
           :project         "projects"
           :membership      "memberships"
           :textblock       "textblocks"
           :timeroll        "timeroll"
           :meeting         "meetings"
           :presence        "presence"
           :notification    "notifications"
           :chat-room       "chat-rooms"
           :chat-message    "chat-messages"
           :comment         "comments"
           :subscription    "subscriptions"
           :document        "documents"
           :cal-item        "cal-items"
           :knowledgebase   "knowledgebase"
           :tag             "tags"
           :ext-session     "extsessions"})

;(defn connect! []
;  (if (-> :default *connections* nil?)
;      (let [conn (mg/connect)]
;        (alter-var-root #'*connections*
;                        assoc :default
;                        { :conn conn
;                          :db   (mg/get-db conn db-name)})))
;  (-> *connections* :default :conn))

(defn get-db []
  (config/C :database :db))


;(defn get-db []
;  (connect!)
;  (-> *connections* :default :db))
