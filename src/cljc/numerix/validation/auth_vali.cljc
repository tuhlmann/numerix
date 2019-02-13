(ns numerix.validation.auth-vali
  (:require
    [taoensso.timbre        :as log]
    [validateur.validation  :as v]
    [cuerdas.core           :as cue]
    [clojure.string         :as str]))

(def special-chars #{ \` \~ \! \@ \# \$ \% \^ \& \* \( \) \_ \- \+ \= \{ \} \[ \] \\ \| \: \; \" \' \< \> \, \. \? \/ })

(def email-regex #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")

(def invitation-emails-split-regex #"\s+")
(def max-emails-per-invitation 50)
(def max-members-per-project 200)

(defn matches-regex?
  "Returns true if the string matches the given regular expression"
  [v regex]
  (boolean (re-matches regex v)))

(defn is-email?
  "Returns true if v is an email address"
  [v]
  (if (nil? v)
    false
    (matches-regex? v email-regex)))

(defn is-multiple-emails?
  "Returns true if v is one or more email addresses separated by space"
  [v]
  (if (nil? v)
    false
    (every? #(is-email? (str/trim %)) (str/split v #"\s+"))))

(defn is-optional-email?
  "Returns true if v is empty or an email address"
  [v]
  (if (empty? v)
    true
    (matches-regex? v email-regex)))

(defn count-tokens [str split-regex]
  (if (nil? str)
    0
    (count (str/split str split-regex))))

(defn max-tokens [str split-regex max]
  (<= (count-tokens str split-regex) max))

(def change-pwd-validations
  (v/validation-set
    (v/validate-with-predicate :password (fn [{:keys [password current] :as p}]
                                          (not= password current)) :message "New password must be different")

    ))

(def pwd-validations
  (v/validation-set

    (v/presence-of :password :message "Password may not be blank")
    (v/length-of :password :within (range 8 30))
    (v/validate-with-predicate :password (fn [{:keys [password]}]
                                             (not (= (cue/upper password) password)))
                               :message "must contain lower case")
    (v/validate-with-predicate :password (fn [{:keys [password]}]
                                             (not (= (cue/lower password) password)))
                               :message "must contain upper case")
    (v/validate-with-predicate :password (fn [{:keys [password]}]
                                             (boolean (some #(cue/numeric? (str %)) password)))
                               :message "must contain digits")
    (v/validate-with-predicate :password (fn [{:keys [password]}]
                                             (boolean (some #(boolean (special-chars %)) password)))
                               :message "must contain special characters")


    ))

(def confirm-pwd-validations
  (v/validation-set
    (v/presence-of :confirm :message "Confirm Password may not be blank")
    (v/validate-with-predicate :confirm (fn [{:keys [password confirm] :as p}]
                                          (= password confirm)) :message "Passwords do not match")))

(def invite-members-validations
  (v/validation-set
    (v/validate-with-predicate :emails (fn [{:keys [emails]}] (is-multiple-emails? emails)) :message "Please enter one or more valid email addresses")
    (v/validate-with-predicate :emails (fn [{:keys [emails]}] (max-tokens
                                                                emails
                                                                invitation-emails-split-regex
                                                                max-emails-per-invitation
                                                                )) :message "Please enter no more than 50 email addresses")))

(def edit-membership-validations
  (v/validation-set
    (v/validate-with-predicate :email (fn [{:keys [email]}] (is-email? email)) :message "Please enter a valid email address")))


