(ns numerix.auth-utils
  (:require [clojure.pprint :as pprint]
            [numerix.lib.roles :as numerix-roles]
            [clojure.string :as str]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.accessrules :refer [success error restrict]]
            [cuerdas.core :as cue]
            [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [validateur.validation :as v]
            [numerix.utils :as u]
            [ring.util.response :refer [response redirect content-type]]
            [clj-time.core :as t]
            [numerix.model.user :as db-user]
            [numerix.model.invoice :as db-invoice]
            [numerix.config :as config]
            [numerix.model.files :as files]
            [numerix.model.document :as document]
            [agynamix.roles :as roles]
            [numerix.model.membership :as member]
            [numerix.model.user-db :as user-db]
            [numerix.auth.form-auth :as form-auth])
  (:import (org.bson.types ObjectId)
           (java.net URI)))

(defn user-id-or-random [user]
  (:_id user (ObjectId/get)))

(defn user-id-str [user]
  (.toString (user-id-or-random user)))

(defn auth-user-map [user]
  {:auth-user {
               :_id (user-id-str user)
               :email (:email user)
               :roles (:roles user) ;(numerix-roles/roles-to-keywords (:roles user))
               :permissions (:permissions user)
               }})

(defn add-auth-info [session user]
  (let [sess (assoc session :identity (keyword (:email user)))
        sess (merge sess (auth-user-map user))]
    sess))

;; see also auth/handle-through-login for a way without additional redirect
(defn redirect-through-login [next-uri]
  (redirect (str "/login?next=" next-uri)))

(defn resolve-uri [context uri]
  (let [context (if (instance? URI context) context (URI. context))]
    (.resolve context uri)))

(defn context-uri
  "Resolves a [uri] against the :context URI (if found) in the provided
   Ring request.  (Only useful in conjunction with compojure.core/context.)"
  [{:keys [context]} uri]
  (if-let [base (and context (str context "/"))]
    (str (resolve-uri base uri))
    uri))

(defn not-authenticated [req handler]
  (if (authenticated? req)
    (redirect "/")
    (handler req)))

(defn authenticated [req handler]
  (if-not (authenticated? req)
    (redirect-through-login (:uri req))
    (handler req)))

(defn request-has-permissions [request permissions]
  (if-let [user-data (get-in request [:session :auth-user])]
    (if (roles/has-permission? user-data permissions)
      true
      (error "Insufficient Privileges"))

    (error "Cannot find user data")))

(defn request-has-permission [request permission]
  (request-has-permissions request #{permission}))

(defn authorized-user-read-access [req]
  (request-has-permission req "user:read"))

(defn authorized-user-edit-access [req]
  (request-has-permission req "user:edit"))

(defn is-admin [user]
  (roles/has-permission? user #{"admin:*"}))

(defn is-invoice-owner [document-id req]
  (enc/if-lets [is-authenticated? (authenticated? req)
                user-id (config/get-auth-user-id-from-req req)
                membership (member/get-membership-by-user-id user-id)
                document (document/get-raw-document document-id)
                doc-invoice-id (:invoice-id document)
                invoice (db-invoice/get-invoice doc-invoice-id)
                has-access? (form-auth/as-form-read? :invoices membership)]

               (do
                 (log/info "is invoice owner")
                 true
                 )

               (do
                 (log/info "is NOT invoice owner")
                 false)))


;;; Redirect middleware

(def redirect-whitelist
  [#"http://localhost:10555/.*"
   #"(http|https)://numerix.at/.*"])

(defn wrap-authorized-redirects [handler]
  (fn [req]
    (let [resp (handler req)
          loc (get-in resp [:headers "Location"])]
      (if (and loc (not (some #(re-matches % loc) redirect-whitelist)))
        (do
          (println "Possible redirect attack: " loc)
          (assoc-in resp [:headers "Location"] "/"))
        resp))))

(defn login-token-valid? [token]
  (log/info "checking login token " token)
  (if-let [user (user-db/get-user-by-token token)]
    (do
      (log/info "found user by token " (pr-str user))
      (t/before? (t/now) (:login-token-expires user)))
    false))

(defn invitation-token-valid? [token]
  (log/info "checking invitation token " token)
  (if-let [membership (member/get-invited-member-by-token token)]
    (do
      (t/before? (t/now) (:expires membership)))
    false))

(defn with-login-token [& {:keys [token on-valid on-error]}]
  (fn [req]
    (not-authenticated req
                       (fn [req]
                         (restrict on-valid
                                   {:handler (fn [_] (login-token-valid? token))
                                    :on-error on-error
                                    })))))

(defn with-invitation-token [& {:keys [token on-valid on-error]}]
  (fn [req]
    (not-authenticated req
                       (fn [req]
                         (restrict on-valid
                                   {:handler (fn [_] (invitation-token-valid? token))
                                    :on-error on-error
                                    })))))

(defn redirect-with-msg [uri msg]
  (fn [req & args]
    (let [resp (-> (redirect (context-uri req uri))
                   (u/add-flash-message msg))]
      resp)))

