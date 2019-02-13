(ns numerix.model.invoice_details
  (:require [numerix.db                     :as db]
            [monger.core                    :as mg]
            [monger.collection              :as mc]
            [monger.operators               :refer :all]
            [numerix.config                 :as config]
            [taoensso.timbre                :as log]
            [clojure.pprint                 :as pprint])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]))

(def invoice-details-coll (:invoice-details db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'invoice-details'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db invoice-details-coll)
      (mc/create db invoice-details-coll {})
      (mc/ensure-index db invoice-details-coll (array-map :author-id 1) { :unique false }))))

;; Low level, DB access functions

(defn increment-invoice-value
  "Reads the specified value from the DB and atomically changes it."
  [user-id key default-value]

  (let [db (db/get-db)
        result (or (mc/find-and-modify db invoice-details-coll
                        {:author-id user-id :key key}
                        {$inc {:value 1}}
                        {:return-new true})

                   (mc/insert-and-return db invoice-details-coll
                                         {:author-id user-id
                                          :key key
                                          :value default-value}))]

    (:value result)))

