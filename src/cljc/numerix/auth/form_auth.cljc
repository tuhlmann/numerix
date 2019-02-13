(ns numerix.auth.form-auth
  (:require
    #?@(:clj [[agynamix.permissions :as p]
              [taoensso.timbre :as log]
              [taoensso.encore :as enc]
              [agynamix.roles :as roles]
              [numerix.lib.roles :as roles-def]]
        :cljs [[agynamix.permissions :as p :refer [Permission]]
               [taoensso.timbre :as log]
               [taoensso.encore :as enc]
               [agynamix.roles :as roles]
               [numerix.lib.roles :as roles-def]])
              [clojure.set :refer [union]])
    #?(:clj
       (:import [agynamix.permissions Permission])))


(defn user-id-matches [user rec]
  (= (:_id user) (:author-id rec)))


(defn as-form-access? [form-name membership]
  (roles/has-any-permission? membership #{"project:access" (str (name form-name) ":access")}))

(defn form-name-permissions [form-names action]
  (if (sequential? form-names)
    (into #{(str "project:" action)} (map #(str (name %) ":" action) form-names))
    #{(str "project:" action) (str (name form-names) ":" action)}))

(defn as-form-read?
  "Tests if there is a read permission for this form-name.
  If no form-name is given it retrieves all permissions of the project-read-only role"
  [form-name membership]
  (let [permissions (form-name-permissions form-name "read")]
    (roles/has-any-permission? membership permissions)))


(defn as-form-read-item?
  "Tests if there is a read permission for this item in form-name."
  [form-name membership user-id form-data]
  (let [permissions (form-name-permissions form-name "read")]
    (let [read-allowed?
          (if (or
                (roles/has-any-permission? membership permissions)
                (and
                  (= user-id (:author-id form-data user-id))
                  (= (:project-id membership) (:project-id form-data (:project-id membership)))))
            (do
              ;(log/info "allowed to edit")
              true)

            (do
              ;(log/info "not allowed to edit")
              false))]

      read-allowed?)))


(defn as-form-create-new? [permission-name membership user-id]
  "Test if the user is allowed to create any new record of the given permission type."

  (let [edit-all-perms (form-name-permissions permission-name "edit")
        edit-own-perms (form-name-permissions permission-name "edit-own")]

    ;(log/info "as-form-edit for " permission-name (pr-str edit-all-perms) (pr-str edit-own-perms) (pr-str (:roles membership)))

    (let [create-allowed?
          (if (or
                (roles/has-any-permission? membership edit-all-perms)
                (roles/has-any-permission? membership edit-own-perms))
            true
            false)]

      ;(log/info "allowed to create? " create-allowed?)
      create-allowed?)))


(defn as-form-edit? [permission-name membership user-id form-data existing-rec]
  "Test if the user is allowed to update this form record.
  Returns the contact record if true, false otherwise.
  if the user has permission project:edit or if user is author and has project:edit-own"

  (let [edit-all-perms (form-name-permissions permission-name "edit")
        edit-own-perms (form-name-permissions permission-name "edit-own")]

    ;(log/info "as-form-edit for " permission-name (pr-str edit-all-perms) (pr-str edit-own-perms) (pr-str (:roles membership)))

    (let [edit-allowed?
          (if (or
                (roles/has-any-permission? membership edit-all-perms)
                (and
                  (= user-id (:author-id form-data user-id))
                  (= (:project-id membership) (:project-id form-data (:project-id membership)))
                  (roles/has-any-permission? membership edit-own-perms)))
            (do
              ;(log/info "allowed to edit")
              true)

            (do
              ;(log/info "not allowed to edit")
              false))

          edit-allowed?
          (if (:_id form-data)
            (and
              edit-allowed?
              (some? existing-rec)
              (= user-id (:author-id form-data user-id) (:author-id existing-rec))
              (= (:project-id form-data (:project-id membership)) (:project-id existing-rec (:project-id membership))))
            edit-allowed?)]

      edit-allowed?)))


(defn as-form-remove? [form-name membership user-id form-data-or-existing-rec]
  "Test if the user is allowed to update this form record.
  Returns the contact record if true, nil otherwise.
  if the user has permission project:edit or if user is author and has project:edit-own"

  (enc/if-let [record-exists (:_id form-data-or-existing-rec)
               can-edit? (as-form-edit? form-name membership user-id form-data-or-existing-rec form-data-or-existing-rec)]

              true
              false))


(defn as-form-switch-prj? [form-name old-membership new-membership user-id form-data existing-rec]
  "Test if the user is allowed to move this record from one project to another."

  ;(log/info "as-form-edit for " form-name)
  (and
    (some? form-data)
    (some? old-membership)
    (some? new-membership)
    (= user-id (:user-id old-membership) (:user-id new-membership))
    (as-form-edit? form-name old-membership user-id form-data existing-rec)
    (as-form-edit? form-name new-membership user-id form-data existing-rec)))

(defn as-form-access-project? [membership user-id project]
  "Test if the user is allowed to access this project record.
  Access is granted as soon as there is a membership record between the user and the project,
  no additional permission necessary."

  (and (some? membership)
       (= (:_id project) (:project-id membership))
       (= user-id (:user-id membership))))

(defn as-form-create-new-project? [user-id]
  "Test if the user is allowed to create any new project record."

  true)

(defn as-form-create-project? [user-id project]
  "Test if the user is allowed to create this new project record."

  (and
    (nil? (:_id project))
    (user-id-matches user-id project))

  ;(log/info "as-form-edit for " form-name)
  )

(defn as-form-edit-project? [membership user-id project existing-rec]
  "Test if the user is allowed to update this project record."

  (and
    (some? existing-rec)
    (roles/has-permission? membership "project-admin:edit"))

  ;(log/info "as-form-edit for " form-name)
  )

(defn as-form-remove-project? [membership user-id current-project-id form-data-or-existing-rec]
  "Test if the user is allowed to update this membership record."

  (and
    (some? form-data-or-existing-rec)
    (:_id form-data-or-existing-rec)
    (roles/has-permission? membership "project-admin:edit")
    (= (:project-id membership) (:_id form-data-or-existing-rec))
    (not= current-project-id (:_id form-data-or-existing-rec))))


(defn as-form-create-new-membership? [my-membership user-id]
  "Test if the user is allowed to create a new membership record."

  (roles/has-permission? my-membership "project-admin:edit"))

(defn as-form-create-membership? [my-membership user-id membership]
  "Test if the user is allowed to create memberships from this record."

  (and
    (nil? (:_id membership))
    (roles/has-permission? my-membership "project-admin:edit")
    (= (:project-id my-membership) (:project-id membership))))

(defn as-form-edit-membership? [my-membership user-id membership existing-rec]
  "Test if the user is allowed to update this membership record."

  (and
    (some? existing-rec)
    (:_id existing-rec)
    (roles/has-permission? my-membership "project-admin:edit")
    (= (:project-id my-membership) (:project-id membership) (:project-id existing-rec))))

(defn as-form-remove-membership? [my-membership user-id form-data-or-existing-rec]
  "Test if the user is allowed to update this membership record."

  (and
    (some? form-data-or-existing-rec)
    (:_id form-data-or-existing-rec)
    (roles/has-permission? my-membership "project-admin:edit")
    (= (:project-id my-membership) (:project-id form-data-or-existing-rec))))

(defn guard-memberships-read [membership fn alt-fn]
  (if (as-form-read? :memberships membership)
    (fn)
    (alt-fn)))

(defn guard-textblocks-read [membership f]
  (if (as-form-read? :invoices membership)
    (f)
    nil))

(defn guard-contacts-read [membership f]
  (if (as-form-read? :contacts membership)
    (f)
    nil))

(defn has-permission? [m perm]
   (roles/has-permission? m perm))