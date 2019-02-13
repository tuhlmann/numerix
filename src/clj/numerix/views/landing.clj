(ns numerix.views.landing
  (:require [ring.util.anti-forgery :refer :all]
            [net.cgrand.enlive-html :as html]
            [net.cgrand.jsoup :as cgrand-jsoup]
            [ring.util.http-response :refer :all]
            [numerix.utils :refer [when-content maybe-content maybe-substitute append-msg-script]]
            [numerix.components.mailer :as mailer]
            [numerix.config :refer [C]]
            [clojure.pprint :as pprint]
            [taoensso.timbre :as log]))

;(net.cgrand.reload/auto-reload *ns*)
(html/set-ns-parser! cgrand-jsoup/parser)

(defn scripts-to-append [context]
  (let [s (str (append-msg-script (:msg context)))]
    ;(log/info "append script " s)
    s))

(html/deftemplate base "templates/base.html"
                  [{:keys [title content scripts]}]
                  [:#title]  (maybe-content title)
                  [:#content] (maybe-substitute content)
                  [:#script-append] (when-content scripts))

(html/defsnippet landing-page "templates/landing.html" [:div#content]
                  [context]
                  [:form] (html/append (html/html-snippet (anti-forgery-field)))
                  [:div#flash-messages] (if (:msg context)
                                          #(html/at %
                                                    [:p.message] (html/content (:msg context)))
                                          nil))

(html/defsnippet terms-of-service-page "templates/terms.html" [:div#content]
                  [context]
                 )


(defn handle-landing-page [& {:keys [context]}]
  (base {:title "Welcome to AGYNAMIX Numerix"
         :content (landing-page context)
         :scripts (scripts-to-append context)}))

(defn terms-of-service [& {:keys [context]}]
  (base {:title "AGYNAMIX Numerix Terms of Service"
         :content (terms-of-service-page context)}))

(defn contact-author
  [request]
  ;(pprint/pprint request)
  (mailer/contact-author (C :mailer) (:params request))
  (ok))

