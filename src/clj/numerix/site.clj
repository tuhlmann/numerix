(ns numerix.site
  (:require [compojure.core :refer [ANY GET POST routes]]
            [compojure.route :refer [not-found resources]]
            [compojure.response :as response]
            [ring.handler.dump :refer [handle-dump]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.http :as http]
            [buddy.auth.accessrules :refer [success error restrict]]
            [numerix.authentication :as auth]
            [numerix.auth-utils :as autil]
            [numerix.views.layout :as layout]
            [numerix.views.landing :as landing]
            [taoensso.timbre :as log]
            [numerix.model.files          :as files]
            [numerix.views.layout         :as lay]
            [numerix.service.file-upload  :as upload]
            [numerix.service.responses    :as resp]
            [clojure.pprint               :as pprint])
  (:import (org.bson.types ObjectId)))

(defn handle-role-user [request]
  (log/info "You are a user")
  "you are a user")

(defn handle-role-admin [request]
  "you are a admin")


(defn route-index-page [req]
  (if (authenticated? req)
    (layout/index req)
    (let [msg (get-in req [:flash :msg])
          context (if msg {:msg msg} nil)]
      (log/debug "context is " context)
      (landing/handle-landing-page :context context))))

(defn route-index-page2 [req]
  (if (authenticated? req)
    (layout/index req)
    (auth/handle-through-login req (:uri req))))

;;; TODO Split rules in authenticated, authenticated
;;; TODO and admin rules blocks for basic protection
;;; Example: http://rundis.github.io/blog/2015/buddy_auth_part2.html
(defn all-routes [ajax-post-fn ajax-get-or-ws-handshake-fn]
  (routes
    (GET "/"          [] route-index-page)
    (resources "/")
    (ANY  "/dmp"      req (autil/authenticated req handle-dump))
    (GET  "/chsk"     [] (restrict ajax-get-or-ws-handshake-fn {:handler autil/authorized-user-read-access
                                                                :on-error  resp/not-authorized}))
    (POST "/chsk"     [] (restrict ajax-post-fn {:handler autil/authorized-user-read-access
                                                 :on-error resp/not-authorized}))
    (GET  "/login"    req (autil/not-authenticated req auth/login))

    (GET  "/login-token/:token" [token]
      (autil/with-login-token :token token
                        :on-valid (fn [req] (auth/reset-password-page req token))
                        :on-error (autil/redirect-with-msg "/" "The provided token is no longer valid.")))

    (POST "/confirm-password-reset" {params :params}
      (autil/with-login-token :token (:login-token params)
                              :on-valid auth/confirm-reset-user-password
                              :on-error (autil/redirect-with-msg "/" "The provided token is no longer valid.")))

    (GET  "/confirm-account/:token" [token]
      (autil/with-login-token :token token
                              :on-valid (fn [req] (auth/unlock-user-account req token)) ;; redirects to login page with message
                              :on-error (autil/redirect-with-msg "/" "The provided token is no longer valid.")))

    (GET  "/confirm-invitation/:token" [token]
      (autil/with-invitation-token :token token
                                   ;; depending on whether the user is already registered in the system:
                                   ;;   - will change the membership status to connected
                                   ;;   - will create a user record, and add the user-id to membership & change connect status
                                   :on-valid (fn [req] (auth/add-user-to-project req token))
                                   :on-error (autil/redirect-with-msg "/" "The provided token is no longer valid.")))

    (POST "/login"    []  auth/login-authenticate)
    (GET  "/register" req (autil/not-authenticated req #(auth/register-page % nil)));(:flash req))))
    (POST "/register" [] auth/register-new-user)
    (GET  "/reset-password" req (autil/not-authenticated req #(auth/request-reset-password-page % (:flash req))))
    (POST "/reset-password" [] auth/send-reset-user-password-request)
    (GET  "/login-as/:user-id" [user-id] (restrict #(auth/login-as-user % user-id) {:handler #(autil/request-has-permission % "admin:*")
                                                                     :on-error               resp/not-authorized}))
    (GET  "/logout"   [] auth/logout)
    (POST "/contact"  []  landing/contact-author)
    (GET  "/terms"    [] (fn [req] (landing/terms-of-service)))
    (GET  "/role-user" []
      (restrict handle-role-user {:handler #(autil/request-has-permission % "user:*"), :on-error resp/not-authorized}))
    (GET "/role-admin" req
      (restrict handle-role-admin {:handler #(autil/request-has-permission % "admin:*"), :on-error resp/not-authorized}))

    ;; API methods
    (GET "/api/invoice/download/:document-id/:file-id" [document-id file-id]
      (restrict (partial resp/download-file-response file-id true true)
                {:handler (partial autil/is-invoice-owner document-id)
                 :on-error (fn [req _] (autil/redirect-through-login (:uri req)))}))

    ; Upload files
    (POST "/api/upload" []
      (restrict upload/handle-upload {:handler autil/authorized-user-edit-access :on-error resp/not-authorized}))

    ; Download files
    (GET "/api/file/:file-id" [file-id]
      ;; FIXME: SEC Check if user is owner of downloaded file!!!
      (restrict (partial resp/download-file-response file-id false true) {:handler autil/authorized-user-read-access :on-error resp/not-authorized}))

    ; Upload profile image
    (GET "/api/profile-image/:profile-image-id" [profile-image-id]
      (restrict (partial upload/get-profile-image profile-image-id) {:handler autil/authorized-user-read-access :on-error resp/not-authorized}))
    (POST "/api/profile-image" []
      (restrict upload/change-profile-image {:handler autil/authorized-user-edit-access :on-error resp/not-authorized}))


    ; Route everything else to the client
    (GET "/*"          [] route-index-page)
    (not-found (resp/render-not-found-page))))
