(ns numerix.auth.user-auth
  (:require [taoensso.timbre        :as log]
            [numerix.auth-utils     :as autil]))

(defn user-id-matches [user-id rec]
  (= user-id (:author-id rec)))

(defn as-admin-user [user fn-or-data]
  "Tests if this user is validated as admin, then executes the given function"

  (if (autil/is-admin user)
    (if (fn? fn-or-data)
      (fn-or-data)
      fn-or-data)
    nil))

(defn is-admin [user]
  "Tests if this user is validated as admin"

  (autil/is-admin user))


;; auth for standard users

(defn user-can-download
  "Tests if this user is allowed to access the given file.
  Currently the user-id must be the same. In the future access will
  be checked by roles."

  [user-id file-meta]
  ;(log/info "Compare " (str user-id) " and " (get-in file-meta [:author-id]) ": " (pr-str file-meta ))
  (= user-id (get-in file-meta [:author-id])))
