(ns numerix.lib.gravatar
  (:require
    #?@(:clj [[buddy.core.hash :as hash]
              [buddy.core.codecs :as codecs]]
        :cljs [[goog.crypt.Md5]])
    [clojure.string :as str]
    [cuerdas.core :as cue]
    [taoensso.timbre :as log]
    [taoensso.encore :as enc]))

(def default-rating "G")
(def default-size 42)
(def default-image "")

(defn int-arr-to-hex-str [arr]
  (reduce (fn [akku i]
            (str akku (cue/pad (.toString i 16) {:length 2 :padding "0"}))) "" arr))

#?(:cljs
   (defn get-md5 [str]
     (let [m (doto (goog.crypt.Md5.)
               (.update str))
           digest (int-arr-to-hex-str (.digest m))]
       digest)))

#?(:clj
   (defn get-md5 [str]
     (-> (hash/md5 str)
         (codecs/bytes->hex))))

(defn image-url
  "- email: The email address of the recipient
  - size: The square size of the output gravatar
  - rating: The rating of the Gravater, the default is G
  - default: The default image to return if none exists for the given email"

  ([email]
   (image-url email default-size  default-rating default-image))

  ([email size]
   (image-url email size  default-rating default-image))

  ([email size rating default]

   (enc/when-lets [url (enc/format "//www.gravatar.com/avatar/%s?s=%s&r=%s" (get-md5 (or email "")) (str size) rating)
                   url (if (> (count default) 0)
                         (enc/format "%s&d=%s" url (enc/url-encode default))
                         url)]
                  url)))


(defn img-tag
  ([email]
   (img-tag email default-size))

  ([email size]
   (img-tag email size default-rating default-image))

  ([email size rating default]
   [:img {:src (image-url email, size, rating, default)}]))