(ns numerix.model.files
  (:require [monger.collection :as mc]
            [monger.operators  :refer :all]
            [taoensso.timbre   :as log]
            [numerix.db        :as db]
            [monger.gridfs     :as gfs])
  (:import (com.mongodb.gridfs GridFS)
           (org.bson.types ObjectId)))

(defn get-gridfs [db]
  (GridFS. db))

(defn get-file [id]
  (let [db (db/get-db)
        fs (get-gridfs db)
        oid (if (string? id) (ObjectId. id) id)]

    (gfs/find-by-id fs oid)))

(defn get-file-info [id]
  (let [db (db/get-db)
        fs (get-gridfs db)
        oid (if (string? id) (ObjectId. id) id)]

    (gfs/find-one-as-map fs {:_id oid})))

(defn get-mult-files-info [ids]
  ;(log/info "find files for ids " ids)
  (let [db (db/get-db)
        fs (get-gridfs db)
        oids (map (fn [id] (if (string? id) (ObjectId. id) id)) ids)]

    (gfs/find-maps fs {:_id {$in oids}})))

(defn remove-file-by-id [id]
  (let [db (db/get-db)
        fs (get-gridfs db)
        oid (if (string? id) (ObjectId. id) id)]

    (gfs/remove fs {:_id oid})))

(defn remove-mult-files-by-id [ids]
  (let [db (db/get-db)
        fs (get-gridfs db)
        oids (map (fn [id] (if (string? id) (ObjectId. id) id)) ids)]

    (when (seq oids)
      (gfs/remove fs {:_id {$in oids}}))))

