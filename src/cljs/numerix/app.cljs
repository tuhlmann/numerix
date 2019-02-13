(ns numerix.app
  (:require [cljsjs.jquery]
            [cljsjs.jquery-ui]
            [cljsjs.tether]
            [cljsjs.bootstrap]
            [cljsjs.quill]
            [cljsjs.cropper]
            [cljsjs.react-select]
            [cljsjs.moment]
            [reagent.core           :as reagent :refer [atom]]
            [reagent.session        :as session]
            [re-frame.db            :as db] ;; direct access until we migrated to re-frame
            [re-frame.core          :as rf]
            [secretary.core         :as secretary :include-macros true]
            [taoensso.timbre        :as log]
            [numerix.routes         :as routes]
            [numerix.state          :as s]
            [numerix.lib.helpers    :as h]
            [numerix.ws             :as socket]
            [numerix.api.user       :as user-api]
            [numerix.site           :as site]
            [numerix.history        :as history]
            [numerix.listeners      :as listeners]
            [numerix.lib.roles      :as numerix-roles]
            [numerix.api.chat       :as chat-api]
            [goog.events            :as events]
            [goog.history.EventType :as EventType])
  (:import [goog.history Html5History EventType]
           [goog.Uri]))
  ;(:import goog.History)


;; Set the global log level.
;; This should be configurable via server run.mode
(log/set-level! :info)


; (session/get :current-page)
(defn current-page [data]
  (swap! data update :re-render-flip not)
  (let [page (session/cursor [:current-page])
        view (session/cursor [:current-view])]

    (add-watch
      session/state
      :session-observer
      (fn [_ _ old-state new-state]
        (let [old-page (:current-page old-state)
              old-id (:selected-item-id old-state)
              new-page (:current-page new-state)
              new-id (:selected-item-id new-state)]
          (when (or
                  (not= old-page new-page)
                  (not= old-id new-id))
            (rf/dispatch [:presence/log-user-presence (or new-page :dashboard-page) new-id])))))

    (fn []
      ;@view
      ;; get-page called too often, fn below does not log presence
      [(routes/get-page (or @page :dashboard-page)) data])))



;;(session/put! :current-page #'home-views/main)

(defn render-root []
  (reagent/render [current-page db/app-db] (.getElementById js/document "react-page")))

(enable-console-print!)

(defn ^:export main []
  ;(log/info "Sente version is " sente/sente-version)
  (rf/dispatch-sync [:initialize])     ;; puts a value into application state
  (history/hook-browser-navigation!)
  (numerix-roles/initialize-roles)
  (site/init-routes)
  (listeners/init-listeners)
  (socket/register-handler :chsk/handshake
    (fn [?data]
      (rf/dispatch [:management/set-csrf-token (second ?data)])
      (rf/dispatch [:management/get-and-swap-initial-data render-root])))
  (socket/init-connection)
  ;(chat-api/setup-heartbeat-service db/app-db)
  )

(main)

