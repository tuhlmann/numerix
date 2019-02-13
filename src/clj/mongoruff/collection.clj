(ns mongoruff.collection
  (:refer-clojure :exclude [find count drop distinct empty? update]
                  :rename {remove core-remove})
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [monger.gridfs :refer [store-file make-input-file filename content-type metadata]]
            [numerix.db :as db]
            [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [clojure.data :as data])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern DBObject WriteResult]
           (com.mongodb.gridfs GridFS)
           (java.io ByteArrayInputStream FileInputStream)
           (clojure.lang IPersistentMap)
           (java.util Map List)))


(def sample-schema
  {
   :db ;; a function to get the db
   :collection ;; the name of the collection
   :update-keys nil ;; fn given a rec will return a list of keys that should be updated. If nil the whole rec will upd
   :after-read-list nil ;; executed on the whole list of results, can change the resulting element list
   :after-read nil ;; executed per result item
   :before-save nil ;; executed before record is inserted / updated
   :after-save nil ;; pass in saved record, can retrieve a new rec from db and return that one with new augments
   }
  )

(defn run-schema-single-record-fn [schema fn-key rec-or-lst]
  (if-let [f (get schema fn-key)]
    (if (sequential? f)
      (reduce (fn [akku func]
                (func akku)) rec-or-lst f)

      (f rec-or-lst))

    rec-or-lst))

(defn run-schema-multi-record-fn
  [schema fn-key lst]
  (if-let [f (get schema fn-key)]
    (reduce (fn [akku rec]
              (let [processed-rec (if (sequential? f)
                                    (reduce (fn [akku func]
                                              (func akku)) rec f)

                                    (f rec))]
                (if (some? processed-rec)
                  (conj akku processed-rec)
                  akku))) nil lst)
    lst))

(defn after-read-list [schema lst]
  (run-schema-single-record-fn schema :after-read-list lst))

(defn after-read [schema lst]
  (run-schema-multi-record-fn schema :after-read lst))


(defn before-save [schema rec]
  (run-schema-single-record-fn schema :before-save rec))

(defn after-save [schema rec]
  (run-schema-single-record-fn schema :after-save rec))


(defn update-record [schema rec db collection]
  (let [rec-bef-save (before-save schema rec)
        write-concern (if (:update-keys schema)
                        (let [update-keys ((:update-keys schema) rec-bef-save)]
                          (mc/update db collection {:_id (:_id rec-bef-save)}
                                     {"$set" (select-keys rec-bef-save update-keys)}
                                     {:multi false}))
                        (mc/update-by-id db collection (:_id rec-bef-save) rec-bef-save))
        rec-aft-save (after-save schema rec-bef-save)]

    rec-aft-save))


(defn insert-record [schema rec db collection]
  (let [rec-bef-save (before-save schema rec)
        all-keys (keys rec-bef-save)
        update-keys (if (:update-keys schema)
                      ((:update-keys schema) rec-bef-save)
                      all-keys)
        [removed-keys _ _] (if (:update-keys schema)
                             (data/diff (into #{} all-keys) (into #{} update-keys))
                             [nil nil nil])
        rec-to-save
        (if (:update-keys schema)
          (apply dissoc rec-bef-save removed-keys)
          rec-bef-save)
        saved-rec (mc/insert-and-return db collection rec-to-save)
        saved-rec
        (if (:update-keys schema)
          (let [removed-map (select-keys rec-bef-save removed-keys)]
            (merge removed-map saved-rec))
          saved-rec)

        rec-aft-save (after-save schema saved-rec)]

    rec-aft-save))


(defn update
  "Insert or update a record according to a schema definition"
  [schema rec]
  {:pre [(some? schema) (some? rec)]}

  (enc/when-let
    [db ((:db schema))
     collection (:collection schema)]

    (if (:_id rec)
      (update-record schema rec db collection)

      (insert-record schema rec db collection)

      )))

(defn query-many
  [schema query-fn]

  (enc/when-let
    [db ((:db schema))
     collection (:collection schema)
     result (query-fn db collection)]

    (let [after-read-list (after-read-list schema result)
          after-read (after-read schema after-read-list)]

      after-read)))