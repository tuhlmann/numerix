(ns numerix.authentication
  (:require
    [hiccup.form :refer :all]
    [hiccup.core :as h]
    [compojure.response :refer [render]]
    [clojure.java.io :as io]
    [ring.util.response :refer [response redirect content-type]]
    [ring.util.anti-forgery :refer [anti-forgery-field]]
    [validateur.validation :as v]
    [clojure.string :as str]
    [clojure.pprint :as pprint]
    [buddy.hashers :as hashers]
    [buddy.auth :refer [authenticated? throw-unauthorized]]
    [numerix.views.layout :as lay]
    [numerix.views.landing :as landing]
    [numerix.auth-utils :as autil]
    [numerix.model.user :as db-user]
    [numerix.model.ext-session :as ext-session]
    [numerix.validation.auth-vali :as auth-vali]
    [numerix.components.mailer :as mailer]
    [numerix.config :refer [C]]
    [numerix.utils :as u]
    [crypto.random :as random]
    [clj-time.core :as t]
    [cuerdas.core :as cue]
    [taoensso.timbre :as log]
    [numerix.config :as config]
    [numerix.model.user :as user]
    [numerix.model.user-db :as user-db]
    [numerix.model.membership :as member]
    [taoensso.encore :as enc]
    [numerix.model.project-db :as project-db]
    [numerix.config :as cfg]
    [numerix.model.presence :as presence])
  (:import (org.bson.types ObjectId)))



;(defn- merge-authentication [m auth]
;  (pprint/pprint (get-in m [:session ::identity]))
;  (update-in m [:session ::identity]
;             #(-> (assoc-in % [:authentications (:identity auth)] auth)
;                (assoc :current (:identity auth)))))


(defn login
  "From http://getbootstrap.com/examples/signin/signin.css"
  [request & [error]]
  (lay/landing-base request
    [:div.login-form
     [:form.form-signin {:role :form
                         :method "POST"
                         :style "border: 1px dotted #555"
                         :action "/login"}
      [:a.close {:href "/"} [:span "&times;"]]
      [:h2.form-signin-heading [:span.fa.fa-user] "&nbsp;&nbsp;Please sign in&#8230"]
      (when error
        [:div.alert.alert-warning
         [:span [:strong "Login Failed. "] error]])

      (lay/login-control :username
                         (lay/my-email-field :username "Email"
                                             {:tabindex 1 :required
                                              :required :value (get-in request [:params :username] "")}))

      (lay/login-control :password
                         (lay/my-password-field :password "Password"
                                                {:tabindex 2}))

      (hidden-field :next-url (get-in request [:params :next] "/"))

      [:div.checkbox
        [:label.checkbox {:type :checkbox}
          [:input {:name "remember" :type :checkbox :value "remember-me"} " Remember me"]]]
      (anti-forgery-field)
      [:button.btn.btn-lg.btn-primary.btn-block {:type "submit"} "Sign in"]
      [:p]
      [:a {:href "/reset-password"} "Reset password"]
      "&nbsp;&nbsp;or&nbsp;&nbsp;"
      [:a {:href "/register"} "Register new user"]]]))


(defn handle-through-login [req next-uri]
  (let [new-req (if next-uri
                  (assoc-in req [:params :next] next-uri)
                  req)]
    (login new-req)))


;; Authenticate Handler
;; Responds to post requests in same url as login and is responsible to
;; identify the incoming credentials and set the appropiate authenticated
;; user into session. `authdata` will be used as source of valid users.
(defn login-authenticate [request]
  (let [username (get-in request [:form-params "username"])
        password (get-in request [:form-params "password"])
        remember-me (= (get-in request [:form-params "remember"]) "remember-me")
        session (:session request)]
    (if-let [found-user (user-db/get-user-by-email username)]
      (if (hashers/check password (:password found-user))
        (if (:validated found-user false)
          (let [nexturl (get-in request [:form-params "next-url"] "/")
                session (autil/add-auth-info session found-user)]
            (-> (redirect nexturl)
                (ext-session/handle-ext-session-flag (:_id found-user) remember-me)
                (assoc :session session)))
          (login request (str "Your account is not yet unlocked. "
                              "We sent you an email with unlock information. "
                              "If you need a new link please click on the 'Reset Password' link below.")))
        (login request "Passwords do not match"))
      (login request "User not found"))))


(defn logout [request]
  (log/info "Execute logout")
  (let [user-id (config/get-auth-user-id-from-req request)
        kill-cookie (ext-session/delete-ext-session user-id)]
    (-> (redirect "/")
        (dissoc :identity)
        (assoc :session {})
        (update-in [:cookies ext-session/cookie-name] (fn [_] kill-cookie))
        (update-in [:cookies "ring-session"] (fn [_] {:value "kill", :max-age 1})))))


(defn register-page
  "From http://getbootstrap.com/examples/signin/signin.css"
  [{{email :email} :params :as request} & [vali-map]]
  (lay/landing-base request
    [:div.login-form
     [:form.form-signin {:role :form
                         :method "POST"
                         :style "border: 1px dotted #555"
                         :action "/register"}

      [:a.close {:href "/"} [:span "&times;"]]
      [:h2.form-signin-heading [:span.fa.fa-user] "&nbsp;&nbsp;Register&#8230"]
      (when vali-map
        (log/info "Vali map is " vali-map)
        [:div.alert.alert-warning
         [:span [:strong "Registration Failed."]]])

      (lay/login-control :email    (lay/my-email-field :email "Email" {:tabindex 1 :required :required :value (or email "")}) vali-map)
      (lay/login-control :password (lay/my-password-field :password "Password" {:tabindex 2}) vali-map)
      (lay/login-control :confirm  (lay/my-password-field :confirm  "Confirm"  {:tabindex 3}) vali-map)
      (hidden-field :next (get-in request [:params :next] "/"))
      (hidden-field :cfrm (get-in request [:params :cfrm] "y"))

      (anti-forgery-field)
      [:p.subtext "By registering you agree to our "
       [:a {:href "/terms" :target "_blank"} "Terms of Service."]]
      [:button.btn.btn-lg.btn-primary.btn-block {:type "submit"} "Register"]
      [:p]
      [:a {:href "/login"} "Already a registered user?"]
      [:br]
      [:a {:href "/reset-password"} "Reset password"]]]))

(defn register-new-user [{params :params :as req}]
  (let [validation
        (v/compose-sets
          (v/validation-set
            (v/presence-of :email :message "Email may not be blank")
            (v/validate-with-predicate :email (fn [{:keys [email]}] (auth-vali/is-email? email)) :message "Please enter a valid email address")
            (v/validate-with-predicate :email (fn [{:keys [email]}] (not (user-db/get-user-by-email email)))
                                       :message-fn #(str "Email " (:email %) " already exists")))

          auth-vali/pwd-validations
          auth-vali/confirm-pwd-validations)

        vali-map (validation params)]

    (if (seq vali-map)
      (register-page req vali-map)

      ;; we do not allow admin to be created through the web interface
      (let [user (user-db/create-new-user (select-keys params [:email :password])) ;; :admin
            added-user (user-db/add-user user)
            ;session (:session resp {})
            ;session (autil/add-auth-info session added-user)
            next-url (or (:next params) (autil/context-uri req "/"))
            confirmation-mail? (= "y" (:cfrm params "y"))
            resp (-> (redirect next-url)
                     (u/add-flash-message (h/html [:span [:strong "Congrats!"] " A new user has been created for you." [:br]
                                                   "We have sent you a confirmation email with a link to unlock your new user." [:br]
                                                   "It should arrive shortly in an inbox near you."])))]
        (when confirmation-mail?
          (mailer/send-confirm-new-user (C :mailer) (:email added-user) (:login-token added-user)))
        (mailer/send-new-user-registered-note (C :mailer) added-user)

        resp))))

(defn request-reset-password-page
  "From http://getbootstrap.com/examples/signin/signin.css"
  [{{email :email} :params :as request} & [vali-map redir-with-msg]]
  (lay/landing-base request
    (when redir-with-msg
      [:div.alert.alert-info
       [:p (:msg redir-with-msg)]
       [:script (str "
        setTimeout(function(){
          window.location.replace('" (:redirect-url redir-with-msg) "');
        }, 4000);
       ")]])
    [:div.login-form
     [:form.form-signin {:role :form
                         :method "POST"
                         :style "border: 1px dotted #555"
                         :action "/reset-password"}

      [:a.close {:href "/"} [:span "&times;"]]
      [:h2.form-signin-heading [:span.fa.fa-user] "&nbsp;&nbsp;Reset&#8230"]
      (when vali-map
        [:div.alert.alert-warning
         [:span [:strong "Something went wrong. Sorry."]]])

      (lay/login-control :email    (lay/my-email-field :email "Email" {:tabindex 1 :required :required :value (or email "")}) vali-map)

      (anti-forgery-field)
      [:button.btn.btn-lg.btn-primary.btn-block {:type "submit"} "Reset Password"]
      [:p]
      [:a {:href "/login"} "Back to login"]]]))

(defn send-reset-user-password-request [{params :params :as req}]
  (let [validation
        (v/validation-set
          (v/presence-of :email :message "Email may not be blank")
          (v/validate-with-predicate :email (fn [{:keys [email]}] (auth-vali/is-email? email)) :message "Please enter a valid email address"))

        vali-map (validation params)]

    (if (seq vali-map)
      (do
        (println "Found errors, going back to reset page")
        (request-reset-password-page req vali-map))

      (if-let [user (user-db/get-user-by-email (:email params))]
        (let [token (user-db/create-login-token)
              expires (t/plus (t/now) (t/hours 4))
              upd-user (user-db/set-login-token user token expires)
              resp (-> (redirect (autil/context-uri req "/"))
                       (u/add-flash-message "We have sent a reset password email to the address you provided"))]
          (mailer/send-password-reset (C :mailer) (:email user) token)
          ;(request-reset-password-page req nil {
          ;                             :msg "We have sent a reset password email to the address you provided"
          ;                             :redirect-url (autil/context-uri req "/")
          ;                             })
          resp)


        (let [err {:email #{"Email address does not exist"}}]
          (request-reset-password-page req err))))))



(defn reset-password-page
  "From http://getbootstrap.com/examples/signin/signin.css"
  [{{email :email} :params :as request} token & [vali-map]]
  (lay/landing-base request
    [:div.login-form
     [:form.form-signin {:role :form
                         :method "POST"
                         :style "border: 1px dotted #555"
                         :action "/confirm-password-reset"}

      [:a.close {:href "/"} [:span "&times;"]]
      [:h2.form-signin-heading [:span.fa.fa-user] "&nbsp;&nbsp;New Password&#8230"]
      (when vali-map
        [:div.alert.alert-warning
         [:span [:strong "Could not set new password."]]])

      (lay/login-control :password  (lay/my-password-field :password "Password" {:tabindex 1 }) vali-map)
      (lay/login-control :confirm   (lay/my-password-field :confirm  "Confirm"  {:tabindex 2 }) vali-map)
      (h/html (hidden-field :login-token token))
      (anti-forgery-field)
      [:button.btn.btn-lg.btn-primary.btn-block {:type "submit"} "Confirm New Password"]]]))

(defn confirm-reset-user-password [{params :params :as req}]
  (log/info "confirm reset user pwd")
  (let [validation
        (v/compose-sets
          auth-vali/pwd-validations
          auth-vali/confirm-pwd-validations)
        vali-map (validation params)]

    (if (seq vali-map)
      (do
        (println "Found errors, going back to reset page")
        (reset-password-page req (:login-token params) vali-map))

      (if-let [user (user-db/get-user-by-token (:login-token params))]
        (let [upd-user (-> user
                           (assoc :password (hashers/encrypt (:password params))
                                  :validated true)
                           (user-db/update-user-keys
                             :set [:password :validated]
                             :unset [:login-token :login-token-expires]))]

          (->
            (redirect (autil/context-uri req "/login"))
            (u/add-flash-message "Thank you for changing your password. Please continue to log in.")))

        (->
          (redirect (autil/context-uri req "/"))
          (u/add-flash-message "We couldn't find your user with the given token."))))))

(defn- do-unlock-user-account [user]
  (-> user
      (assoc :validated true)
      (user-db/update-user-keys
        :set [:validated]
        :unset [:login-token :login-token-expires]))
  )

(defn unlock-user-account [req token]
  (if-let [user (user-db/get-user-by-token token)]
    (let [_ (do-unlock-user-account user)]

      (->
        (redirect (autil/context-uri req "/login"))
        (u/add-flash-message "Thank you for validating your email address. Please continue to log in.")))

    (->
      (redirect (autil/context-uri req "/"))
      (u/add-flash-message "We couldn't find your user with the given token."))))

(defn add-user-to-project [req token]
  (enc/if-let
    [membership (member/get-invited-member-by-token token)
     project (project-db/get-project (:project-id membership))]

    (if-let [user (user-db/get-user-by-email (:email membership))]

      (do
        ;; user exists, change connection-status
        (let [new-membership
              (member/update-membership (-> membership
                                            (assoc :connection-status :active
                                                   :user-id (:_id user))
                                            (dissoc :token :expires)))]
          (when-not (:validated user)
            ;; the invitation token counts as email address validation
            (do-unlock-user-account user))

          (->
            (redirect (autil/context-uri req (str "/project/" (:project-id new-membership))))
            (u/add-flash-message (str "You have been added to project '" (:name project) "'")))))

      (do
        ;; user does not yet exist, need to create one
        (->
          (redirect (autil/context-uri req (str
                                             "/register?email="
                                             (:email membership)
                                             "&cfrm=n&next="
                                             (str cfg/host "/confirm-invitation/" token))))
          (u/add-flash-message (str "Please finish your user registration in order to join '" (:name project) "'"))
        ))

      )))

(defn login-as-user
  "Logs the current user out and in as the given user.
  The route is protected so only admins can access this."
  [req user-id]
  (if-let [user (user-db/get-user (ObjectId. user-id))]
    (do
      (let [resp (->
                   (redirect (autil/context-uri req "/"))
                   (assoc :session (autil/add-auth-info {} user))
                   (u/add-flash-message (str "You are logged in as " (:email user))))]
        (log/info "Response " (pr-str resp))
        resp))


    (do
      (log/info "error with found user ")
     (->
       (redirect (autil/context-uri req "/"))
       (u/add-flash-message "A problem occured.")))))
