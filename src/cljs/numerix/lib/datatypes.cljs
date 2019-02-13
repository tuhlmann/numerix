(ns numerix.lib.datatypes
  (:require [cuerdas.core :as cue]
            [clojure.string :as str]
            [cljs-time.coerce :refer [from-long]]
            [taoensso.timbre :as log])
  (:import [goog.date UtcDateTime]))

(def keycodes {:ESC 27
               :TAB 9
               :ENTER 13
               :BACKSPACE 8
               :LEFT 37
               :UP 38
               :RIGHT 39
               :DOWN 40
               :COMMA 188})

(declare objectid-eq)

(defn oid-time [id]
  (from-long (* (js/parseInt (subs (str id) 0 8) 16) 1000)))

(defprotocol ObjectId
  (getTime [this]))

(deftype ^:no-doc ObjectId [object-id]

  ObjectId
  (getTime [this]
    (oid-time object-id))

  Object
  (toString [this]
    object-id)

  IEquiv
  (-equiv [o other]
    (and (instance? ObjectId other)
         (objectid-eq o other)))
  )

(defn objectid? [oid]
  (instance? ObjectId oid))

(defn objectid-eq [id1 id2]
  (cond
    (and (not(nil? id1)) (not(nil? id2)))
    (= (.toString id1) (.toString id2))

    (and (nil? id1) (nil? id2))
    true

    :else false
    )
  )

(defn objectid-neq [id1 id2]
  (not= (.toString id1) (.toString id2)))


;(extend-type ObjectId
;  IEquiv
;  (-equiv [o other]
;    (and (instance? ObjectId other)
;         (objectid-eq o other))))


(defn hstr-to-min [str]
  (let [p (cue/parse-double str)
        p (if (js/isNaN p) 0 p)]
    (js/Math.round (* p 60))))

(defn min-to-hstr [min]
  (if-not (nil? min)
    (str (/ (js/Math.round (* (/ min 60) 100)) 100))
    ""))

(defn str-to-float [str]
  (cue/parse-double str))

(defn float-to-str [v]
  (if-not (nil? v)
    (str (/ (js/Math.round (* v 100)) 100))
    ""))

(defn str-to-int [str]
  (cue/parse-int str))

(defn int-to-str [v]
  (if-not (nil? v)
    (str v)
    ""))

(defn str-to-bool [str]
  (cue/to-bool str))

(defn bool-to-str [b]
  (str b))