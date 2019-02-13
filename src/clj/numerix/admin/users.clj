(ns numerix.admin.users
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.model.user :as db-user]
            [numerix.auth.user-auth :as a-auth]
            [numerix.components.mailer :as mailer]
            [numerix.config :refer [C]]
            [clj-time.core :as t]
            [numerix.model.user-db :as user-db]
            [numerix.model.user :as user]))


;; FIXME Needs support for pagination
(defn list-users []
  (let [db (db/get-db)
        result (mc/find-maps db user-db/user-coll)
        sorted (sort-by #(:email %) result)]
    (into [] sorted)))

(defn update-user [user]
  (let [db (db/get-db)]
    (if (:_id user)
      (let [all-keys (into #{} (keys user))
            use-keys (into [] (apply disj all-keys user-db/secured-keys))]
        (user-db/update-user-keys user :set use-keys))

      (mc/insert-and-return db user-db/user-coll user))))

(defn remove-user [user]
  (let [db (db/get-db)]
    (log/info "user entry: " (pr-str user))
    (mc/remove-by-id db user-db/user-coll (:_id user))))


;;; WS Communication functions

(defmethod event-msg-handler :admin/list-users
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [auth-user (config/get-auth-user-from-req ring-req)
     users (a-auth/as-admin-user auth-user list-users)]

    [:admin/list-users { :code :ok, :data { :users users}}]
    [:admin/list-users { :code :error :msg "Not authorized"}]))

(defmethod event-msg-handler :admin/post-user
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [auth-user (config/get-auth-user-from-req ring-req)
     edit-user (if (:_id ?data)
                   (a-auth/as-admin-user auth-user ?data)
                   (a-auth/as-admin-user auth-user ?data))
     upd-user (update-user edit-user)]

    [:admin/post-user { :code :ok, :msg "User updated" :data { :user upd-user}}]
    [:admin/post-user { :code :error :msg "Not authorized"}]))

(defmethod event-msg-handler :admin/remove-user
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [auth-user  (config/get-auth-user-from-req ring-req)
     edit-user  (when (:_id ?data)
                  (a-auth/as-admin-user auth-user ?data))
     upd-user (remove-user edit-user)]

    [:admin/remove-user { :code :ok :msg (str "User " (:email edit-user) " removed.")}]
    [:admin/remove-user { :code :error :msg "Not authorized"}]))


(defmethod event-msg-handler :admin/reset-pwd-token
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [auth-user  (config/get-auth-user-from-req ring-req)
     edit-user  (when (:_id ?data)
                  (a-auth/as-admin-user auth-user ?data))
     token      (user-db/create-login-token)
     expires    (t/plus (t/now) (t/hours 4))
     _          (user-db/set-login-token edit-user token expires)
     upd-user   (user-db/get-user (:_id edit-user))
     _          (mailer/send-password-reset (C :mailer) (:email upd-user) token)]

    [:admin/reset-pwd-token { :code :ok, :msg (str "Reset password email sent to " (:email upd-user) ) :data { :user upd-user}}]
    [:admin/reset-pwd-token { :code :error :msg "Not authorized"}]))

(defmethod event-msg-handler :admin/clear-login-token
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [auth-user  (config/get-auth-user-from-req ring-req)
     edit-user  (when (:_id ?data)
                  (a-auth/as-admin-user auth-user ?data))
     upd-user   (user-db/update-user-keys edit-user
                                          :unset [:login-token :login-token-expires])]

    [:admin/clear-login-token { :code :ok, :msg "Cleared Reset Token" :data { :user upd-user}}]
    [:admin/clear-login-token { :code :error :msg "Not authorized"}]))


(defmethod event-msg-handler :admin/login-as-token
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (enc/when-lets [auth-user (config/get-auth-user-from-req ring-req)
                  edit-user  (when (:_id ?data)
                               (a-auth/as-admin-user auth-user ?data))]

                 (log/info "log in as " (:email edit-user))))



