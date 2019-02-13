(ns numerix.model.chat-db
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.service.html-cleaner :as html-cleaner]
            [numerix.model.project :as prj]
            [numerix.config :refer [C]]
            [numerix.lib.helpers :as h]
            [taoensso.encore :as enc]
            [numerix.components.ws :as ws]
            [numerix.model.presence :as presence]
            [clj-time.core :as t]
            [mongoruff.collection :as ruff]
            [monger.query :as mq]
            [numerix.model.project-db :as project-db]))

(def chat-msg-coll (:chat-message db/coll))
(def chat-room-coll (:chat-room db/coll))

(defn chat-room-maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db chat-room-coll)
      (mc/create db chat-room-coll {})
      (mc/ensure-index db chat-room-coll (array-map :related-type 1) { :unique false })
      (mc/ensure-index db chat-room-coll (array-map :related-id 1) { :unique false }))))



(defn chat-message-maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db chat-msg-coll)
      (mc/create db chat-msg-coll {})
      (mc/ensure-index db chat-msg-coll (array-map :ts 1) { :unique false }))))


(def message-schema
  {
   :db (fn[] (db/get-db))
   :collection chat-msg-coll
   :update-keys h/list-keys-without-augments
   :after-read-list nil ;; executed on the whole list of results, can change the resulting element list
   :after-read nil
   :before-save (fn [rec]
                  (let [new-rec (assoc rec :message (h/escape-html-tags (:message rec)))]
                    (log/info "before-save created " new-rec)
                    new-rec
                    ))
   :after-save nil ;; pass in saved record, can retrieve a new rec from db and return that one with new augments
   }
  )

(def chat-room-schema
  {
   :db (fn[] (db/get-db))
   :collection chat-room-coll
   :update-keys h/list-keys-without-augments
   :after-read-list nil ;; executed on the whole list of results, can change the resulting element list
   :after-read nil
   :before-save nil
   :after-save nil ;; pass in saved record, can retrieve a new rec from db and return that one with new augments
   }
  )

;; Low level, DB access functions


(defn get-chat-message [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db chat-msg-coll id)))

(defn remove-chat-message [chat-msg]
  (let [db (db/get-db)]
    (mc/remove-by-id db chat-msg-coll (:_id chat-msg))))


(defn list-chat-messages [chat-room-id max-messages last-loaded-ts]
  (let [qry {:chat-room-id chat-room-id}
        qry (if last-loaded-ts
              (merge qry
                     {:ts {$lt last-loaded-ts}})
              qry)]
    (ruff/query-many
      message-schema
      (fn [db coll]
        (let [sorted (mq/with-collection db coll
                                         (mq/find qry)
                                         ;; it is VERY IMPORTANT to use array maps with sort
                                         (mq/sort (array-map :ts -1))
                                         (mq/limit max-messages))]
          (into [] (reverse sorted)))))))



(defn update-chat-message [chat-msg]
  ;(log/info "saving chat msg " (pr-str chat-msg))
  (ruff/update message-schema chat-msg)
  )

;(defn update-chat-message [chat-msg]
;  (let [db (db/get-db)]
;    (if (:_id chat-msg)
;      (do
;        (mc/update-by-id db chat-msg-coll (:_id chat-msg) chat-msg)
;        chat-msg)
;      (mc/insert-and-return db chat-msg-coll chat-msg))))



;;; ********************************************************
;;; CHAT ROOM FUNCTIONS
;;; ********************************************************


(defn get-chat-room [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db chat-room-coll id)))


;(defn create-chat-room [related-type related-id]
;  (let [db (db/get-db)
;        chat-room {:related-type related-type
;                   :related-id related-id}]
;
;    (mc/insert-and-return db chat-room-coll chat-room)))

;(defn get-or-create-chat-room-by-related [related-type related-id]
;  (let [db (db/get-db)
;        room (first (mc/find-maps db chat-room-coll {:related-type related-type
;                                                     :related-id related-id}))]
;
;    (if room
;      room
;      (create-chat-room related-type related-id))))


;(defn attach-chat-room
;  "Search chatrooms for an existing room for this record"
;  [page item-id rec]
;  (let [chat-room (get-or-create-chat-room-by-related page item-id)]
;    (assoc rec :__chat-room-id (:_id chat-room))))

(defn list-chat-rooms [user]
  (ruff/query-many
    chat-room-schema
    (fn [db coll]
      (let [prj-ids (project-db/visible-project-ids user)
            sorted (mq/with-collection db coll
                                       (mq/find {:project-id {$in prj-ids}})
                                       ;; it is VERY IMPORTANT to use array maps with sort
                                       (mq/sort (array-map :date -1)))]
        (into [] sorted)))))

(defn update-chat-room [chat-room]
  ;(log/info "saving chat msg " (pr-str chat-room))
  (ruff/update chat-room-schema chat-room)
  )
