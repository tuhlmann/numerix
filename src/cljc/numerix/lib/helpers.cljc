(ns numerix.lib.helpers
  (:require
    #?@(:clj [[clj-time.core :as t]
              [clj-time.format :refer [formatter formatters parse unparse]]]
        :cljs [[cljs-time.core :as t]
               [cljs-time.format :refer [formatter formatters parse unparse]]
               [goog.string      :as gstring]])
    [numerix.lib.gravatar :as gravatar]
    [taoensso.encore      :as enc]
    [taoensso.timbre      :as log]
    [cuerdas.core         :as cue]
    [clojure.string       :as str]
    [agynamix.roles       :as roles])
  #?(:clj
     (:import [java.lang.Math])))

;; How long will the cookie be valid
(def cookie-expires-days 14)
(def invite-expires-days 14)

(defn isAdmin [user-data]
  (roles/has-permission? user-data "admin:*"))

(defn positions
  [pred coll]
  (keep-indexed (fn [idx x]
                  (when (pred x)
                    idx))
                coll))

(defn iso8601->date [iso8601]
  (when (seq iso8601)
    (parse (formatters :basic-date) iso8601)))

(defn date->iso8601 [date]
  (unparse (formatters :basic-date) date))

(defn format-iso8601 [format iso8601]
  (when (seq iso8601)
    (let [d (iso8601->date iso8601)]
      (unparse (formatter format) d))))

(defn format-date [format d]
  (unparse (formatter format) d))

#?(:cljs
   (defn format-local-date [format d]
     (unparse (formatter format) (t/to-default-time-zone d))))

(defn short-rand []
  (str "f" (enc/uuid-str 13)))

#?(:cljs
   (defn unescape [s]
     (gstring/unescapeEntities s)))


(defn escape-html-tags [str]
  (-> str
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

#?(:cljs
   (defn tty-log [& data]
     (apply enc/log (map #(clj->js %) data))))

(defmacro tryo
  "tries to execute a body. If that throws an exception will return a default value"
  [body def-value]
  `(try
     ~@body
     (catch #?(:clj Exception
               :cljs :default) ex# ~@def-value)))

(defn shorten
  "Shorten text if longer than `max-len`. Take `max-len - (count append)
  characters and append `append`."
  [max-len append text]

  (if (> (count text) max-len)
    (str (subs text 0 (- max-len (count append))) append)

    text))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn vec-remove
  "remove elem in coll. Returns a new vector without the removed element"
  [coll pos]
  (vec (concat (subvec coll 0 pos) (subvec coll (inc pos)))))

(defn vec-move
  "move elem in coll from from-idx to to-idx"
  [coll from-idx to-idx]
  (let [elem (get coll from-idx)]
    (if (< from-idx to-idx)
      (vec (concat
             (subvec coll 0 from-idx)
             (subvec coll (inc from-idx) (inc to-idx))
             (vector elem)
             (subvec coll (inc to-idx))))

      (vec (concat
             (subvec coll 0 to-idx)
             (vector elem)
             (subvec coll to-idx from-idx)
             (subvec coll (inc from-idx))))

          )))

(defn ref-or-val [value]
  #?(:clj (if (instance? clojure.lang.IDeref value) @value value))
  #?(:cljs (if (satisfies? IDeref value) @value value)))

(defn map-keys [f m]
  (into {}
        (for [[k v] m]
          [(f k) v])))

(defn keywordize-map [m]
  (map-keys keyword m))

(defn strip-augments
  "By convention augmented keys to a model record start with '_'.
  This function removes all keys starting with this prefix or one specified."
  ([m]
   (strip-augments m "__"))

  ([m prefix]
   (let [aug-keys (filter (fn [key]
                            (and (cue/starts-with? (name key) prefix)
                                 (not= (name key) "_id")
                                 ))  (keys m))]

     (apply dissoc m aug-keys))))

(defn list-keys-without-augments [m]
    (keys (strip-augments m)))

(def size-metric [[1 "B"] [1024 "KB"] [1024 "MB"] [1024 "GB"]])

#?(:cljs
   (defn round [v]
     (if-not (nil? v)
       (/ (js/Math.round (* v 100)) 100)
       0)))

#?(:clj
   (defn round [v]
     (if-not (nil? v)
       (/ (Math/round (* v 100.0)) 100.0)
       0)))

(defn readable-file-size [bytes]
  (let [result (reduce (fn [[accu-bytes accu-metric] [m-bytes m-m]]
                         (let [divide (/ accu-bytes m-bytes)]
                           (if (> divide 1)
                             [divide m-m]
                             [accu-bytes accu-metric]))) [bytes ""] size-metric)]

  (str (round (first result)) (second result))))

(defn calc-expires-date
  "Calculate the date when this extended session expires"
  [plus-days]
  (t/plus (t/now) (t/days plus-days)))

(defn name-or-email [user]
  (or (:name user) (:email user)))

(defn name-and-email [user]
  (if-let [name (:name user)]
    (str name " <" (:email user) ">")
    (:email user)))


(defn profile-img-src [profile-img-id email]
  (if profile-img-id
    (str "/api/profile-image/" profile-img-id)

    (gravatar/image-url
      email 60
      gravatar/default-rating "https://numerix.at/img/profile.png")))

