(ns numerix.components.mailer
  (:require
    [taoensso.timbre :as log]
    [numerix.config :as cfg]
    [clojurewerkz.mailer.core :refer [with-settings build-email deliver-email]]
    [postal.core :as postal]
    [clojure.pprint :as pprint]
    [clojure.core.async :as a :refer [>! <! >!! <!! put! go go-loop chan buffer close! thread alts! alts!! timeout sliding-buffer]]
    [com.stuartsierra.component :as component]
    [numerix.lib.helpers :as h]
    [numerix.lib.route-helpers :as route-helpers]))

(def wait-for-reconnect 60000)

(defrecord Mailer [mail-channel]
  component/Lifecycle

  (start [component]
    (if mail-channel
      component

      (let [component (component/stop component)
            channel (chan (sliding-buffer 1000))]

        (log/info "Async Mailer started")
        (go-loop []
          (when-let  [new-email (<! channel)]
            (when (cfg/dev-mode?)
              (log/info "New email to send: ")
              (pprint/pprint new-email)
            )
            (try
              (postal/send-message cfg/email-conn new-email)
              (catch Exception e
                (log/error "Exception sending email: " e)
                (Thread/sleep wait-for-reconnect)
                (>!! channel new-email) ;; put the mail back into the channel and re-try
                                      ;; remove the email after X retries and send mail to admin!
              ))
            (recur)))
        (assoc component
          :mail-channel channel))))

  (stop [component]
    (log/info "Async Mailer stopped")
    (when-let [channel (:mail-channel component)]
      (close! channel))
    (assoc component
      :mail-channel nil)))

;; Constructor function
(defn new-mailer []
  (map->Mailer {}))

(defn- sendMail [mailer email]
  (>!! (:mail-channel mailer) email))

;; API Functions

(defn contact-author [mailer {:keys [name] :as msg}]

  (let [email-obj (build-email {:from cfg/email-from
                                :to [cfg/contact-email]
                                :subject (str "Numerix Contact Message from " name)}
                               "email/contact-form.txt" msg :text/plain)]
    (sendMail mailer email-obj)))


(defn send-password-reset [mailer email token]

  (let [url (str cfg/host "/login-token/" token)
        email-obj (build-email {:from cfg/email-from
                                :to [email]
                                :subject "You have requested a password reset"}
                               "email/reset-password-mail.txt" {:email email, :reset-url url} :text/plain)]
    (sendMail mailer email-obj)))


(defn send-confirm-new-user [mailer email token]

  (let [url (str cfg/host "/confirm-account/" token)
        email-obj (build-email {:from cfg/email-from
                                :to [email]
                                :subject "Please confirm your new user account"}
                               "email/confirm-new-user-account.txt" {:email email, :confirm-account-url url} :text/plain)]
    (sendMail mailer email-obj)))

(defn send-new-user-registered-note
  "This mail is sent to the admin of the site so we know when new users register"
  [mailer new-user]

  (let [email-obj (build-email {:from cfg/email-from
                                :to [cfg/contact-email]
                                :subject (str "Numerix: " (:email new-user) " just registered")}
                               "email/new-user-registration-note.txt"
                               {:name (:name new-user) :email (:email new-user)} :text/plain)]

    (sendMail mailer email-obj)))

(defn send-project-invitation
  "This mail is sent to the invited user"
  [mailer project inviter invitation]

  (let [url (str cfg/host "/confirm-invitation/" (:token invitation))
        m (if (:message invitation)
            (str (h/name-or-email inviter) " added this personal message:\n\n" (:message invitation))
            "")
        email-obj (build-email {:from    cfg/email-from
                                :to      [(:email invitation)]
                                :subject (str "Numerix: " (h/name-or-email inviter) " invited you to join project " (:name project))}
                               "email/project-invitation-message.txt"
                               {:inviter-name-or-email (h/name-or-email inviter)
                                :project-name (:name project)
                                :invitation-expire-date (h/format-date "dd.MM.yyyy" (:expires invitation))
                                :invitation-expire-days h/invite-expires-days
                                :confirm-invitation-url url
                                :optional-inviter-message m
                                } :text/plain)]

    (sendMail mailer email-obj)))

(defn send-chat-notification
  "This mail is sent when a user was called out in a chat message."
  [mailer notification caller called-member chat-msg]

  (let [email-obj (build-email {:from cfg/email-from
                                :to [(:email called-member)]
                                :subject (str "Numerix: " (h/name-or-email caller) " mentioned you in a chat")}
                               "email/chat-notification.txt"
                               {:caller-name (h/name-or-email caller)
                                :called-name (:__user-name called-member)
                                :chat-message-url (route-helpers/make-chat-route
                                                    {:host cfg/host
                                                     :related-type (:related-type notification)
                                                     :related-id (:related-id notification)
                                                     :chat-msg-id (:chat-msg-id notification)})
                                :chat-message (:message chat-msg)} :text/plain)]

    (sendMail mailer email-obj)))

