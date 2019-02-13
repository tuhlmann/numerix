(ns numerix.api.user
  (:require
    [taoensso.timbre :as log]
    [numerix.lib.helpers :as h]
    [numerix.ws :as socket]
    [numerix.state :as s]
    [numerix.history :as hist]
    [re-frame.core :as rf]))

(def conn-timeout 5000)

(defn get-current-user [callback]
  (socket/chsk-send! [:state/get-userdata] conn-timeout
                     (fn [result]
                       ;(log/infof "yihaa Callback received: %s" (pr-str (last result)))
                       (callback (last result))
                       )))

(defn get-initial-data [callback]
  (socket/chsk-send! [:user/get-initial-data
                      [:server-config
                       :user
                       :my-memberships
                       :view-settings
                       :projects
                       :contacts
                       :textblocks
                       :memberships
                       :notifications]]
                     conn-timeout
                     (fn [result]
                       ;(h/tty-log "Initial User Data: " (last result))
                       (callback (last result)))))

(defn post-user [user callback]
  (socket/chsk-send! [:user/post-userdata user] conn-timeout
                     (fn [result]
                       ;(log/infof "yihaa Callback received: %s" (pr-str (last result)))
                       (callback (last result)))))

(defn post-change-user-password [pwd-data callback]
  (socket/chsk-send! [:user/post-change-user-password pwd-data] conn-timeout
                     (fn [result]
                       ;(log/infof "pwd-data Callback received: %s" (pr-str (last result)))
                       (callback (last result)))))

(defn push-view-settings [view-state]
  (socket/chsk-send! [:user/save-view-settings view-state]))

(defn post-contact-message [contact-msg callback]
  (socket/chsk-send! [:contact/post-contact-message contact-msg] conn-timeout
                     (fn [response]
                       (callback (last response)))))

(defn save-user-data [user]
  ;(log/info "User Data: " (pr-str user))
  (post-user user
             (fn [response]
               ;(log/info "response is " (pr-str response))
               (if (socket/response-ok response)
                 (let [user-data (get-in response [:data :user])]
                   (rf/dispatch [:user/update-user-success user-data]))))))

(defn change-user-password [pwd-data]
  (post-change-user-password pwd-data
                             (fn [response]
                               (log/info "response is " (pr-str response))
                               (if (socket/response-ok response)
                                 (rf/dispatch [:user/change-user-password-success {:type :info :timeout 5 :msg (:msg response)}])

                                 (do
                                   (rf/dispatch [:common/add-alert {:type :error :timeout 10 :msg (:msg response)}]))
                                 ))))

(defn reset-user-data [user]
  ;(log/info "Reset User Data: " (pr-str user))
  (get-current-user
    (fn [response]
      (let [user-data (get-in response [:data :user])]
        (rf/dispatch [:user/update-user-success user-data])))))

(defn remove-profile-image [success-fn]
  (socket/send! [:user/remove-profile-image] 5000
                (fn [response]
                  (log/info "remove-profile-image response " (pr-str response))
                  (success-fn (last response)))))

(defn send-contact-message [contact-msg]
  (post-contact-message @contact-msg #(hist/go-to-home)))
