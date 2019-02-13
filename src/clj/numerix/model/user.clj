(ns numerix.model.user
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [clojure.pprint :as pprint]
            [numerix.config :refer [C]]
            [numerix.components.mailer :as mailer]
            [numerix.utils :refer [auth-let]]
            [buddy.hashers :as hashers]
            [validateur.validation :as v]
            [numerix.validation.auth-vali :as auth-vali]
            [cuerdas.core :as cue]
            [crypto.random :as random]
            [clj-time.core :as t]
            [clojure.string :as str]
            [numerix.model.files :as files]
            [numerix.lib.roles :as roles]
            [numerix.model.membership :as member]
            [numerix.model.user-db :as user-db]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]))

(defmethod event-msg-handler :state/get-userdata
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/debug "GET user")
  ;(pprint/pprint ev-msg)
  (if ?reply-fn
    (let [user-id (config/get-auth-user-id-from-req ring-req)
          user (user-db/remove-secure-fields (user-db/get-user user-id))]
      (log/debug "GOT "(prn-str user))
      (?reply-fn [:state/get-userdata { :code :ok, :data { :user user}}]))))

(defmethod event-msg-handler :user/post-userdata
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/debug "POST user " (pr-str ?data))
  (let [user-id (config/get-auth-user-id-from-req ring-req)
        upd-user (user-db/update-user-by-id user-id ?data)
        user (user-db/remove-secure-fields (user-db/get-user user-id))]
    (log/debug "update result " (pr-str upd-user))
    (if ?reply-fn
      (do
        (log/debug "GOT "(prn-str user))
        (?reply-fn [:user/post-userdata { :code :ok, :data { :user user}}])))))


(defmethod event-msg-handler :user/post-change-user-password
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (pprint/pprint ?data)
  (enc/if-lets
    [found-user (user-db/get-user-by-req ring-req)
     vali-set (v/compose-sets
                auth-vali/pwd-validations
                auth-vali/change-pwd-validations
                auth-vali/confirm-pwd-validations)
     validation (vali-set ?data)]

    (let [[code msg]
          (if (hashers/check (:current ?data) (:password found-user))
            (if (v/valid? validation)
              (do
                (user-db/change-user-password (:_id found-user) (:password ?data))
                [:ok "Your Password has been sucessfully changed."])

              [:error validation])

            [:error "Current password does not match"])]
      (when ?reply-fn
        (?reply-fn [:user/post-change-user-password { :code code :msg msg}])))


    (when ?reply-fn
      (?reply-fn [:user/post-change-user-password { :code :error :msg "Could not change your password"}]))))


(defmethod event-msg-handler :user/save-view-settings
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/debug "POST user view settings " (pr-str ?data))
  (when-let [user-id (config/get-auth-user-id-from-req ring-req)]
    (let [upd-view-settings (user-db/update-view-settings user-id ?data)]
      (log/debug "user id is " (pr-str user-id))
      (log/debug "upsert result is " upd-view-settings))))

(defmethod event-msg-handler :contact/post-contact-message
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (mailer/contact-author (C :mailer) ?data)
  (when ?reply-fn
    (?reply-fn [:contact/post-contact-message { :code :ok}])))


(defmethod event-msg-handler :project/activate
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     project-rec (when ?data
                   (project-db/get-project ?data))
     membership (member/get-membership (:_id project-rec) user-id)
     has-access? (form-auth/as-form-access-project? membership user-id project-rec)
     upd-user (user-db/set-current-project user-id (:_id project-rec))]

    [:project/activate-project { :code :ok}]
    [:project/activate-project { :code :error :msg "Error activating project"}]))


(defmethod event-msg-handler :user/remove-profile-image
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (auth-let
    ?reply-fn
    [user-id (config/get-auth-user-id-from-req ring-req)
     user (user-db/get-user user-id)
     profile-image-id (:profile-image-id user)
     _ (do (files/remove-file-by-id profile-image-id) true)
     upd-user (user-db/remove-secure-fields (user-db/update-user-keys user :unset [:profile-image-id]))]

    [:user/remove-profile-image { :code :ok :data { :user upd-user}}]
    [:user/remove-profile-image { :code :error :msg "Error removing profile image"}]))


