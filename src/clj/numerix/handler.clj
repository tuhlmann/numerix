(ns numerix.handler
  (:require [ring.middleware.defaults     :refer [site-defaults wrap-defaults]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [prone.middleware             :refer [wrap-exceptions]]
            [ring.middleware.reload       :as reload]
            [ring.middleware.session      :as session]
            [compojure.response           :refer [render]]
            [ring.util.response           :refer [response redirect content-type]]
            [clojure.java.io              :as io]
            [clojure.string]
            [buddy.auth                   :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session  :refer [session-backend]]
            [buddy.auth.middleware        :refer [wrap-authentication wrap-authorization]]
            [noir.validation              :refer [wrap-noir-validation]]
            [noir.session                 :refer [wrap-noir-session]]
            [com.akolov.enlive-reload     :refer [wrap-enlive-reload]]
            [numerix.model.ext-session    :as ext-session]
            [taoensso.timbre              :as log]
            [numerix.auth-utils           :as autil]
            [numerix.config               :as cfg]))


(defn- form-params [request]
  (merge (:form-params request)
         (:multipart-params request)))

(defn ring-defaults-config []
  (-> ring.middleware.defaults/site-defaults

      (assoc-in [:security :anti-forgery]
                {:read-token (fn [request]
                               (or (-> request form-params (get "__anti-forgery-token"))
                                   (-> request :headers (get "x-csrf-token"))
                                   (-> request :headers (get "x-xsrf-token"))
                                   (-> request :params :csrf-token)))})
      (assoc-in [:security :hsts] false) ;; production problem when true
      (assoc :proxy (cfg/production-mode?))
      (assoc-in [:session :flash] true)
      (assoc-in [:session :cookie-attrs] {
                                          :http-only true
                                          :secure (cfg/production-mode?)})))


;; Self defined unauthorized handler
;; This function is responsible of handling unauthorized requests.
;; (When unauthorized exception is raised by some handler)

(defn unauthorized-handler
  [request metadata]
  (cond
    ;; If request is authenticated, raise 403 instead
    ;; of 401 (because user is authenticated but permission
    ;; denied is raised).
    (authenticated? request)
    (-> (render (slurp (io/resource "error.html")) request)
        (assoc :status 403))

    ;; In other cases, redirect it user to login.
    :else
    (let [current-url (:uri request)]
      (redirect (format "/login?next=%s" current-url)))))

(def auth-backend
  (session-backend {:unauthorized-handler unauthorized-handler}))

;(defn wrap-spy [handler]
;  (fn [request]
;    (println "REQUEST")
;    (pprint/pprint request)
;    (handler request)))

;; Middleware for spying on the doings of other middleware:
(defn html-escape [string]
  (clojure.string/escape string {\< "&lt;", \> "&gt;"}))

(defn html-preformatted-escape [string]
  (str "<pre>\n" (html-escape string) "</pre>\n"))

(defn format-request [name request kill-keys kill-headers]
  (let [r1 (reduce dissoc request kill-keys)
        r (reduce (fn [h n] (update-in h [:headers] dissoc n)) r1 kill-headers)]
    (with-out-str
      (println "-------------------------------")
      (println name)
      (println "-------------------------------")
      (clojure.pprint/pprint r)
      (println "-------------------------------"))))

;; I have taken the liberty of removing some of the less fascinating entries from the request and response maps, for clarity
(def kill-keys [])
(def kill-headers [])

(defn wrap-spy [handler spyname]
  (fn [request]
    (let [incoming (format-request (str spyname ":\n Incoming Request:") request kill-keys kill-headers)]
      (println incoming)
      (let [response (handler request)]
        (let [outgoing (format-request (str spyname ":\n Outgoing Response Map:") response kill-keys kill-headers)]
          (println outgoing)
          (update-in response  [:body] (fn[x] (str (html-preformatted-escape incoming) x  (html-preformatted-escape outgoing)))))))))

(defn ignore-trailing-slash
  "Modifies the request uri before calling the handler.
  Removes a single trailing slash from the end of the uri if present.

  Useful for handling optional trailing slashes until Compojure's route matching syntax supports regex.
  Adapted from http://stackoverflow.com/questions/8380468/compojure-regex-for-matching-a-trailing-slash"
  [handler]
  (fn [request]
    (let [uri (:uri request)]
      (handler (assoc request :uri (if (and (not (= "/" uri))
                                            (.endsWith uri "/"))
                                     (subs uri 0 (dec (count uri)))
                                     uri))))))

(defn the-application [handler is-dev?]
  (if is-dev?
    (-> handler
        (wrap-authorization auth-backend)
        (wrap-authentication auth-backend)
        ;(wrap-spy "SPY")
        ext-session/wrap-extended-session
        (wrap-defaults (ring-defaults-config))
        autil/wrap-authorized-redirects
        ignore-trailing-slash
        wrap-exceptions
        reload/wrap-reload
        wrap-enlive-reload)
    (-> handler
        (wrap-authorization auth-backend)
        (wrap-authentication auth-backend)
        ext-session/wrap-extended-session
        (wrap-defaults (ring-defaults-config))
        autil/wrap-authorized-redirects
        ignore-trailing-slash)))

