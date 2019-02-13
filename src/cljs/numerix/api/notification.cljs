(ns numerix.api.notification
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.lib.messages :refer [push-msg-handler]]
            [reagent.core :as r]
            [reagent.interop      :refer-macros [$ $!]]
            [re-frame.core :as rf]
            [cljs.core.async :as a]
            [clojure.string :as string]
            [taoensso.encore :as enc]
            [numerix.lib.helpers :as h]))


(defn subscribe [subscription]
  (socket/chsk-send! [:remote-subscription/subscribe subscription]))

(defn cancel-subscription [subscription]
  (socket/chsk-send! [:remote-subscription/cancel subscription]))

;; Notifications

(defn send-browser-notification [notification]
  ;; FIXME sending the profile img of the user along or a group img to be shown as icon.
  (let [title (str (:author-name notification) " send you a message:")
        options {:body (:__message notification)
                 :icon (:__author-profile-img-src notification)}
        notification (js/Notification. title (clj->js options))]

    (js/setTimeout #(.close notification) 3000)

    ))

(defn mark-notification-read [notification]
  (socket/chsk-send! [:notification/mark-as-read (:_id notification)]))

(defn remove-all-notifications []
  (socket/chsk-send! [:notification/remove-all-notifications]))

(defn check-browser-notification [notification]

  (when (fn? ($ js/window :Notification))

    (cond
      (= ($ js/Notification :permission) "granted")
      (send-browser-notification notification)

      (not= ($ js/Notification :permission) "denied")
      (.requestPermission js/Notification (fn [permission]
                                            (if (= permission "granted")
                                              (send-browser-notification notification))))


      )))


(defmethod push-msg-handler :notification/new-notification
  [[_ notification]]
  ;(log/info ":notification/new-notification " notification)
  (rf/dispatch [:notification/new-notification-from-server notification])
  )

(defmethod push-msg-handler :notification/updated-notification
  [[_ notification]]
  ;(log/info ":notification/updated-notification " notification)
  (rf/dispatch [:notification/updated-notification-from-server notification]))

(defmethod push-msg-handler :notification/remove-all-notifications
  [_]
  (rf/dispatch [:notification/remove-all-notifications]))

