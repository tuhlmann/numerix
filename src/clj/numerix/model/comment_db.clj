(ns numerix.model.comment-db
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
            [monger.query :as mq]))

(def comments-coll (:comment db/coll))

(defn comments-maybe-init
  "If it doesn't exist yet, it creates a collection named 'comments'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db comments-coll)
      (mc/create db comments-coll {})
      (mc/ensure-index db comments-coll (array-map :related-type 1) { :unique false })
      (mc/ensure-index db comments-coll (array-map :related-id 1) { :unique false }))))

(def comment-schema
  {
   :db (fn[] (db/get-db))
   :collection comments-coll
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

;; Low level, DB access functions

(defn get-comment [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db comments-coll id)))

(defn remove-comment [comment]
  (let [db (db/get-db)]
    (mc/remove-by-id db comments-coll (:_id comment))))


(defn list-comments [related-id max-messages last-loaded-ts]
  (let [qry {:related-id related-id}
        qry (if last-loaded-ts
              (merge qry
                     {:ts {$lt last-loaded-ts}})
              qry)]
    (ruff/query-many
      comment-schema
      (fn [db coll]
        (let [sorted (mq/with-collection db coll
                                         (mq/find qry)
                                         ;; it is VERY IMPORTANT to use array maps with sort
                                         (mq/sort (array-map :ts -1))
                                         (mq/limit max-messages))]
          (into [] (reverse sorted)))))))

(defn update-comment [comment]
  ;(log/info "saving chat msg " (pr-str comment))
  (ruff/update comment-schema comment)
  )
