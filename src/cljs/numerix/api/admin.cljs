(ns numerix.api.admin
  (:require [taoensso.timbre :as log]
            [reagent.interop :refer-macros [$ $!]]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [numerix.views.common :as c]
            [cljs-time.core :refer [now local-date-time days minus day-of-week date-time]]
            [cljs-time.format :refer [formatter formatters parse unparse]]))

;(defn do-post-user [user callback]
;  (socket/send! [:admin/post-user user] 5000
;                     (fn [result]
;                       ;(log/info "post admin user, Callback received: " (pr-str result))
;                       (callback (last result)))))


(defn list-users [max success-fn]
  (socket/send! [:admin/list-users max] 5000
                (fn [response]
                  ;(log/info "list admin users result " (pr-str result))
                  (success-fn (last response)))))

(defn save-user [user success-fn]
  (socket/send! [:admin/post-user user] 5000
                (fn [response]
                  ;(log/info "response is " (pr-str response))
                  (if (socket/response-ok (last response))
                    (do
                      (success-fn (last response)))))))

(defn remove-user [user success-fn]
  (socket/send! [:admin/remove-user user] 5000
                  (fn [response]
                    ;(log/info "response is " (pr-str response))
                    (if (socket/response-ok (last response))
                      (success-fn (last response))))))

(defn reset-pwd-token [user success-fn]
  (socket/send! [:admin/reset-pwd-token user] 5000
                (fn [response]
                  (if (socket/response-ok (last response))
                    (success-fn (last response))))))

(defn clear-login-token [user success-fn]
  (socket/send! [:admin/clear-login-token user] 5000
                (fn [response]
                  (if (socket/response-ok (last response))
                    (success-fn (last response))))))

(defn new-user-rec []
  ;(log/info "Current user id " (user-api/user-id user))
  {
   :created (now)
   })


