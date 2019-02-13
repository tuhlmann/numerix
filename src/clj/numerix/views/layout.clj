(ns numerix.views.layout
  (:require [hiccup.page            :refer [html5 include-css include-js]]
            [hiccup.element         :refer [link-to]]
            [hiccup.form            :refer :all]
            [hiccup.util            :refer [to-uri]]
            [validateur.validation  :as v]
            [clojure.string         :as str]
            [taoensso.timbre        :as log]
            [numerix.utils          :refer [append-msg-script]]))


(defn base [& content]
  (html5
    [:head
     [:title "Welcome to Numerix"]
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "description" :content "An Invoicing application without the pain"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     ;(include-css "//fonts.googleapis.com/css?family=Open+Sans:400,700,300&amp;subset=latin,vietnamese")
     (include-css "//fonts.googleapis.com/css?family=Roboto:300,400,500,700,400italic")
     (include-css "//fonts.googleapis.com/css?family=Roboto+Condensed:400,300")
     (include-css "//fonts.googleapis.com/css?family=Noto+Serif:400,400italic,700,700italic")
     ;(include-css "//fonts.googleapis.com/icon?family=Material+Icons")
     (include-css "/css/style.css")]
    [:body content]
    (include-js "/js/vendor.min.js")))

(defn landing-base [request & content]
  (let [msg (get-in request [:flash :msg])]
    (html5 {:id "landing-page"}
      [:head
       [:title "Welcome to Numerix"]
       [:meta {:charset "utf-8"}]
       [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
       [:meta {:name "description" :content "An Invoicing application without the pain"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:link {:href "//fonts.googleapis.com/css?family=Open+Sans:400,700,300&amp;subset=latin,vietnamese"
               :rel "stylesheet" :type "text/css"}]
       (include-css "/css/landing.css")]
      [:body {:style "background-color: inherit;"} content]
           (include-js "https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.5/umd/popper.min.js")
           (include-js "/js/landing.min.js")
           (when msg
             [:script (append-msg-script msg)]))))

(defn index [req]
  (base
    [:div#react-page]
    (include-js "/js/app.js")))

#_(defn- input-field
   "Creates a new <input> element."
   [type name value]
   [:input {:type  type
            :name  (make-name name)
            :id    (make-id name)
            :value value}])

(defn on-error
  "If the given field has an error, execute func and return its value"
  [field vali-map func]
  (if-let [errs (v/errors field vali-map)]
    (func errs)))

(defn my-field [name placeholder type & [attr]]
  (let [all-attrs (merge attr {:name name
                               :placeholder placeholder
                               :type type                              
                               :autofocus :autofocus})]
    [:input.form-control all-attrs]))

(defn my-password-field [name placeholder & [attr]]
  (let [all-attrs (merge attr {:name name
                               :placeholder placeholder
                               :type :password
                               :required :required
                               :autofocus :autofocus})]
    [:input.form-control all-attrs]))

(defn my-text-field [name placeholder & [attr]]
  (my-field name placeholder :text attr))

(defn my-email-field [name placeholder & [attr]]
  (my-field name placeholder :email attr))


(defn error-item [errors]
  [:div.help-block (str/join ", " errors)])

(defn control [id label field & [vali-map]]
  (let [vali-or-empty (or vali-map {})] 
    [:div {:class (str "form-group" (if (v/errors? id vali-map) " has-error" ""))}
      (list
        label field
        (on-error id vali-or-empty error-item)
        [:br])]))

(defn login-control [id field & [vali-map]]
  (let [vali-or-empty (or vali-map {})]
    [:div {:class (str "form-group"
                       (if (v/errors? id vali-or-empty)
                         " has-danger has-error "
                         ""))}
      (list
        field
        (on-error id vali-or-empty error-item))]))

