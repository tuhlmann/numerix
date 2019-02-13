(ns numerix.history
  (:require [clojure.string :as str]
            [secretary.core :as secretary]
            [reagent.session :as session]
            [reagent.interop :refer-macros [$ $!]]
            [numerix.site :as site]
            [cuerdas.core :as cuerdas]
            [taoensso.timbre :as log]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [re-frame.core :as rf])
  (:import goog.history.Html5History
           goog.history.EventType
           goog.Uri))

(def path-prefix (str js/window.location.protocol "//" js/window.location.host))

(defn get-token []
  (str js/window.location.pathname js/window.location.search))


(deftype MyTokenTransformer []
  Object

  (retrieveToken [this pathPrefix location]
    ;(log/info "retrieveToken " pathPrefix)
    (str (subs (.-pathname location) 0 (count pathPrefix))
         (.-search location)
         (.-hash location)))

  (createUrl [this token pathPrefix location]
    ;(log/info "Create URL " (str pathPrefix token))
    (str pathPrefix token)))


;(defonce history (Html5History. js/window (MyTokenTransformer.)))
(defonce history (Html5History.))

(declare current-route?)

(defn- dispatch-url-change [token]
  (when-not (current-route? token)
    (session/put! :current-view token)
    ;(log/info "dispatch on " token)
    (secretary/dispatch! token)))

(defn uri-to-location [uri]
  (let [path (.getPath uri)
        query (.getQuery uri)]
    (str path (if (cuerdas/empty? (cuerdas/trim query)) "" "?") (cuerdas/trim query))))

(defn- handle-url-change [e]
  ;; log the event object to console for inspection
  ;;(js/console.log e)
  ;; and let's see the token
  ;;(js/console.log (str "Navigating: " (get-token) " , " js/window.location.pathname " ## " js/window.location.search))
  ;; we are checking if this event is due to user action,
  ;; such as click a link, a back button, etc.
  ;; as opposed to programmatically setting the URL with the API
  (when-not (.-isNavigation e)
    ;; in this case, we're setting it
    #_(js/console.log "Token set programmatically "))

  ;; dispatch on the token
  (dispatch-url-change (get-token)))


(defn- find-href
  "Given a DOM element that may or may not be a link, traverse up the DOM tree
  to see if any of its parents are links. If so, return the href content."
  [e]
  ((fn [e]
     (if-let [href (.-href e)]
       href
       (when-let [parent (.-parentNode e)]
         (recur parent)))) (.-target e)))

(defn- is-our-own-path [path]
  (let [s1 (cuerdas/lower path)
        s2 (cuerdas/lower js/window.location.host)]
    (cuerdas/includes? s1 s2)))

(defn- prevent-reload-on-known-path
  "Create a click handler that blocks page reloads for known routes in
  Secretary."
  [history]
  (events/listen js/document "click"
                 (fn [e]
                   (let [href (find-href e)
                         uri (.parse Uri href)
                         path (.getPath uri)
                         location (uri-to-location uri)
                         title (.-title (.-target e))]
                     ;(log/info "Clicked " href " : " path " : " location)
                     (when (and
                             (is-our-own-path href)
                             (secretary/locate-route path))
                       ;(js/window.history.pushState title "" location)
                       ;(dispatch-url-change location)
                       ;(set! js/window.location.search "")
                       (. history (setToken location title))
                       (.preventDefault e))))))

(defn hook-browser-navigation!
  "Create and configure HTML5 history navigation."
  []
  (doto history
    (.setUseFragment false)
    (.setPathPrefix path-prefix)
    (goog.events/listen EventType.NAVIGATE
                        ;; wrap in a fn to allow live reloading
                        #(handle-url-change %))
    (.setEnabled true))
    (prevent-reload-on-known-path history))


(defn map->params [query]
  (let [params (map #(name %) (keys query))
        values (vals query)
        pairs (partition 2 (interleave params values))]
    (str/join "&" (map #(str/join "=" %) pairs))))

(defn navigate!
  "add a browser history entry. updates window/location"
  ([route] (navigate! route {}))
  ([route query]
   ;(log/info "NAVIGATE " route " and " (pr-str query))
   (let [token (.getToken history)
         old-route (first (str/split token "?"))
         query-string (map->params (reduce-kv (fn [valid k v]
                                                (if v
                                                  (assoc valid k v)
                                                  valid)) {} query))
         with-params (if (empty? query-string)
                       route
                       (str route "?" query-string))]

     ;; TODO: There is a problem with setToken that somehow duplicates the query params
     (if (= old-route route)
       (do
         (. history (replaceToken with-params))
         ;(js/window.history.replaceState nil "" with-params)
         ;(dispatch-url-change with-params)
         )
       (do
         (. history (setToken with-params))
         ;(js/window.history.pushState nil "" with-params)
         ;(dispatch-url-change with-params)
       )
       ;(. history (replaceToken with-params))
       ;(. history (setToken with-params))

       ))))


(defn dispatch-current! []
  "Dispatch current URI path."
  (let [path (-> js/window .-location .-pathname)
        query (-> js/window .-location .-search)
        hash (-> js/window .-location .-hash)
        route (str path query hash)]

    (session/put! :current-view path)
    (secretary/dispatch! route)))

(defn navigate-and-dispatch!
  ([route] (navigate-and-dispatch! route {}))
  ([route query]
   (navigate! route query)
   (dispatch-current!)))

(defn get-query-param [key]
  (let [uri (.parse Uri (get-token))
        query-data (.getQueryData uri)]

    (if (.containsKey query-data key)
      (.get query-data key)
      nil)))

(defn update-query-param
  "Update query parameters of the current route. Does not trigger navigation.
  `replace?` if true will replace the current route, otherwise will update it
  creating a new history entry."
  ([key value] (update-query-param key value false))
  ([key value replace?]

  (let [uri (.parse Uri (get-token))
        query-data (.getQueryData uri)
        qd2 (.set query-data key value)
        uri2 (.setQueryData uri qd2)
        location (uri-to-location uri2)]

    ;(log/info "Updated Location " location)

    ;(if replace?
    ;  (js/window.history.replaceState nil "" location)
    ;  (js/window.history.pushState nil "" location)))))

    (if replace?
      (. history (replaceToken location))
      (. history (setToken location))))))

(defn current-route? [route]
  (let [clean-route (str/replace-first route "#" "")]
    (or (= (session/get :current-view "/") clean-route)
        (and (empty? (session/get :current-view)) (= clean-route "/")))))

(defn go-to-home []
  (navigate! (site/home-route)))

