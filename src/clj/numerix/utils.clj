(ns numerix.utils
  (:require [taoensso.encore :as enc :refer [if-lets when-lets]]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as str]))

(defmacro maybe-substitute
  ([expr] `(if-let [x# ~expr] (html/substitute x#) identity))
  ([expr & exprs] `(maybe-substitute (or ~expr ~@exprs))))

(defmacro maybe-content
  ([expr] `(if-let [x# ~expr] (html/content x#) identity))
  ([expr & exprs] `(maybe-content (or ~expr ~@exprs))))

(defmacro when-content
  "when the content is not nil call html/content on it, otherwise returns nil.
  Enline will effectively remove the matching tag from the template if the given value is nil."
  ([expr] `(if-let [x# ~expr] (html/content x#) nil))
  ([expr & exprs] `(when-content (or ~expr ~@exprs))))

;; taken from taoensso.encore
(defmacro auth-let
  "Like `if-let` but binds multiple values iff all tests are true."
  ([reply-fn bindings then] `(auth-let ~reply-fn ~bindings ~then nil))
  ([reply-fn bindings then else]
   (let [[b1 b2 & bnext] bindings]
     (if bnext
       `(if-let [~b1 ~b2]
          ~(condp = (first bnext)
             :when `(if ~(second bnext)
                      (auth-let ~reply-fn ~(vec (drop 2 bnext)) ~then ~else)
                      (if ~reply-fn (~reply-fn ~else) ~else))

             `(auth-let ~reply-fn ~(vec bnext) ~then ~else))

          (if ~reply-fn (~reply-fn ~else) ~else))

       `(if-let [~b1 ~b2]
          (if ~reply-fn (~reply-fn ~then) ~then)
          (if ~reply-fn (~reply-fn ~else) ~else))))))


(defmacro when-lets2
  "Like `when-let` but binds multiple values iff all tests are true."
  [bindings & body]
  (let [[b1 b2 & bnext] bindings]
    (if bnext
      `(when-let [~b1 ~b2] (when-lets2 ~(vec bnext) ~@body))
      `(when-let [~b1 ~b2] ~@body))))


(defn date [date-string])

(defn day-from [d])

(defn add-flash-message
  "Adds a message to the :flash storage in session"
  [response msg]
  (update response :flash assoc :msg msg))

(defn append-msg-script [msg]
  (when msg
    (let [m (str/replace msg "'" "\\x27")]
      (str "
      $(document).ready(function() {
        $.snackbar({
          content: '" m "',
          closeBtn: \"<i class='fa fa-close'></i>\",
          style: \"snackbar-with-close toastx\",
          timeout: 0,
          htmlAllowed: true
        });
      });"))))

