(ns numerix.model.initial-load
  (:require [numerix.db :as db]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [numerix.model.project :as project]
            [numerix.model.membership :as member]
            [numerix.model.notification :as notification]
            [numerix.model.contact :as contact]
            [numerix.model.textblock :as textblock]
            [numerix.model.user :as user]
            [numerix.config :refer [C]]
            [numerix.utils :refer [auth-let]]
            [numerix.model.user-db :as user-db]
            [numerix.auth.form-auth :as form-auth]))

(defmethod event-msg-handler :user/get-initial-data
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/debug "GET user and view settings: " ?data)
  ;(pprint/pprint ev-msg)
  (let [raw-user (user-db/get-user-by-req ring-req)
        user-with-prj (if-not (:current-project raw-user)
                        (if-let [p (first (project/list-projects-for-user raw-user true))]
                          (user-db/set-current-project (:_id raw-user) (:_id p))

                          raw-user)

                        raw-user)
        membership (member/get-membership (:current-project user-with-prj) (:_id user-with-prj))
        result-data
        (reduce merge {}
                (map
                  #(condp = %
                     :server-config {:server-config (config/server-config-for-client)}
                     :user {:user (user-db/remove-secure-fields user-with-prj)}
                     :my-memberships {:my-memberships (member/list-memberships-for-user (:_id user-with-prj))}
                     :view-settings {:view-settings (user-db/get-view-settings (:_id user-with-prj))}
                     :projects {:projects (project/list-projects-for-user user-with-prj true)}
                     :memberships
                     {:memberships (form-auth/guard-memberships-read
                                     membership
                                     (fn [] (member/list-memberships-for-project-auth user-with-prj))
                                     (fn [] (member/list-memberships-for-project-reduced user-with-prj)))}
                     :notifications {:notifications (notification/list-notifications user-with-prj)}
                     :textblocks
                     {:textblocks (form-auth/guard-textblocks-read membership (fn [] (textblock/list-textblocks-for-user user-with-prj)))}
                     :contacts
                     {:contacts (form-auth/guard-contacts-read membership (fn [] (contact/list-contacts-for-user user-with-prj)))}
                     nil)
                  ?data))]
    (when ?reply-fn
      ;(log/debug "GOT "(pr-str result-data))
      (?reply-fn [:user/get-initial-data { :code :ok, :data result-data}]))))

