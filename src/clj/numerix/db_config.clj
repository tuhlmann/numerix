(ns numerix.db-config
  (:require [numerix.model.user-db]
            [numerix.model.ext-session]
            [numerix.model.contact]
            [numerix.model.invoice]
            [numerix.model.invoice_details]
            [numerix.model.project-db]
            [numerix.model.membership]
            [numerix.model.textblock]
            [numerix.model.timeroll]
            [numerix.model.meeting]
            [numerix.model.presence]
            [numerix.model.notification]
            [numerix.model.subscription]
            [numerix.model.chat-db]
            [numerix.model.chat-message]
            [numerix.model.comment-db]
            [numerix.model.comment]
            [numerix.model.document]
            [numerix.model.knowledgebase]
            [numerix.model.tag]
            [numerix.model.calendar]
            [numerix.admin.users]
            [numerix.model.initial-load]
            [monger.core :as mg])
  (:import (com.mongodb WriteConcern)))

(defn initialize-db []
  (mg/set-default-write-concern! WriteConcern/JOURNALED)
  (numerix.model.user-db/maybe-init)
  (numerix.model.ext-session/maybe-init)
  (numerix.model.contact/maybe-init)
  (numerix.model.project-db/maybe-init)
  (numerix.model.membership/maybe-init)
  (numerix.model.invoice/maybe-init)
  (numerix.model.invoice_details/maybe-init)
  (numerix.model.textblock/maybe-init)
  (numerix.model.timeroll/maybe-init)
  (numerix.model.presence/maybe-init)
  (numerix.model.notification/maybe-init)
  (numerix.model.subscription/maybe-init)
  (numerix.model.chat-db/chat-room-maybe-init)
  (numerix.model.chat-db/chat-message-maybe-init)
  (numerix.model.comment-db/comments-maybe-init)
  (numerix.model.meeting/maybe-init)
  (numerix.model.document/maybe-init)
  (numerix.model.calendar/maybe-init)
  (numerix.model.tag/maybe-init)
  (numerix.model.knowledgebase/maybe-init))


