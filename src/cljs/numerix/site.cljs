(ns numerix.site
  (:require [reagent.core :as reagent :refer [atom]]
            [secretary.core :as secretary :include-macros true]
            [numerix.state :as s]
            [reagent.session :as session]
            [cuerdas.core :as str]
            [taoensso.timbre :as log]
            [re-frame.core :as rf]))

;; -------------------------
; Routes
;(secretary/set-config! :prefix "#")
;
(secretary/defroute home-route "/" []
                    (session/put! :current-page :dashboard)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :dashboard])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute contact-route "/contact" []
                    (session/put! :current-page :contact)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :contact])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute imprint-route "/imprint" []
                    (session/put! :current-page :imprint)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :imprint])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute settings-route "/settings" []
                    (session/put! :current-page :settings)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :settings])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute password-route "/password" []
                    (session/put! :current-page :password)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :password])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute project-route "/project/:projectId" [projectId]
                    (session/put! :current-page :projects)
                    (session/put! :selected-item-id projectId)
                    ;(rf/dispatch [:session-field-change :current-page :projects])
                    ;(rf/dispatch [:session-field-change :selected-item-id projectId])
                    )

(secretary/defroute projects-route "/projects" []
                    (session/put! :current-page :projects)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :projects])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute membership-route  "/membership/:memberId" [memberId]
                    (session/put! :current-page :memberships)
                    (session/put! :selected-item-id memberId)
                    ;(rf/dispatch [:session-field-change :current-page :memberships])
                    ;(rf/dispatch [:session-field-change :selected-item-id memberId])
                    )

(secretary/defroute memberships-route "/memberships" []
                    (session/put! :current-page :memberships)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :memberships])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute address-route "/address/:address-id" [address-id]
                    (session/put! :current-page :contacts)
                    (session/put! :selected-item-id address-id)
                    ;(rf/dispatch [:session-field-change :current-page :contacts])
                    ;(rf/dispatch [:session-field-change :selected-item-id address-id])
                    )

(secretary/defroute addressbook-route "/addressbook" []
                    (session/put! :current-page :contacts)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :contacts])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute invoice-route "/invoice/:invoiceId" [invoiceId]
                    (session/put! :current-page :invoices)
                    (session/put! :selected-item-id invoiceId)
                    ;(rf/dispatch [:session-field-change :current-page :invoices])
                    ;(rf/dispatch [:session-field-change :selected-item-id invoiceId])
                    )

(secretary/defroute invoices-route "/invoices" []
                    (session/put! :current-page :invoices)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :invoices])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute chat-room-route "/chat-room/:chat-room-id" [chat-room-id]
                    (session/put! :current-page :chat-room)
                    (session/put! :selected-item-id chat-room-id)
                    ;(rf/dispatch [:session-field-change :current-page :chat-room])
                    ;(rf/dispatch [:session-field-change :selected-item-id chat-room-id])
                    )

(secretary/defroute chat-rooms-route "/chat-rooms" []
                    (session/put! :current-page :chat-room)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :chat-room])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute meeting-route "/meeting/:meeting-id" [meeting-id]
                    (session/put! :current-page :meetings)
                    (session/put! :selected-item-id meeting-id)
                    ;(rf/dispatch [:session-field-change :current-page :meetings])
                    ;(rf/dispatch [:session-field-change :selected-item-id meeting-id])
                    )

(secretary/defroute meetings-route "/meetings" []
                    (session/put! :current-page :meetings)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :meetings])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute cal-item-route "/calendar-item/:cal-item-id" [cal-item-id]
                    (session/put! :current-page :calendar)
                    (session/put! :selected-item-id cal-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :documents])
                    ;(rf/dispatch [:session-field-change :selected-item-id documentId])
                    )

(secretary/defroute calendar-route "/calendar" []
                    (session/put! :current-page :calendar)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :meetings])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute document-route "/document/:documentId" [documentId]
                    (session/put! :current-page :documents)
                    (session/put! :selected-item-id documentId)
                    ;(rf/dispatch [:session-field-change :current-page :documents])
                    ;(rf/dispatch [:session-field-change :selected-item-id documentId])
                    )

(secretary/defroute documents-route "/documents" []
                    (session/put! :current-page :documents)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :documents])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute timeroll-entry-route "/timeroll-entry/:timerollEntryId" [timerollEntryId]
                    (session/put! :current-page :timeroll)
                    (session/put! :selected-item-id timerollEntryId)
                    ;(rf/dispatch [:session-field-change :current-page :timeroll])
                    ;(rf/dispatch [:session-field-change :selected-item-id timerollEntryId])
                    )

(secretary/defroute timeroll-route "/timeroll" []
                    (session/put! :current-page :timeroll)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :timeroll])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute textblock-route "/textblock/:textblockId" [textblockId]
                    (session/put! :current-page :textblocks)
                    (session/put! :selected-item-id textblockId)
                    ;(rf/dispatch [:session-field-change :current-page :textblocks])
                    ;(rf/dispatch [:session-field-change :selected-item-id textblockId])
                    )

(secretary/defroute textblocks-route "/textblocks" []
                    (session/put! :current-page :textblocks)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :textblocks])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute knowledge-entry-route "/knowledge-entry/:knowledge-entry-id" [knowledge-entry-id]
                    (session/put! :current-page :knowledgebase)
                    (session/put! :selected-item-id knowledge-entry-id)
                    ;(rf/dispatch [:session-field-change :current-page :knowledgebase])
                    ;(rf/dispatch [:session-field-change :selected-item-id knowledge-entry-id])
                    )

(secretary/defroute knowledgebase-route "/knowledgebase" []
                    (session/put! :current-page :knowledgebase)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :knowledgebase])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )



;;; ADMIN

(secretary/defroute admin-users-route "/admin/users" []
                    (session/put! :current-page :admin-users)
                    (session/remove! :selected-item-id)
                    ;(rf/dispatch [:session-field-change :current-page :admin-users])
                    ;(rf/dispatch [:session-field-remove :selected-item-id])
                    )

(secretary/defroute admin-user-route "/admin/user/:userId" [userId]
                    (session/put! :current-page :admin-users)
                    (session/put! :selected-item-id userId)
                    ;(rf/dispatch [:session-field-change :current-page :admin-users])
                    ;(rf/dispatch [:session-field-change :selected-item-id userId])
                    )

;;; The not-found route
;(def application
;  (.-body js/document))
;
;(defn set-html! [el content]
;  (aset el "innerHTML" content))
;
;(secretary/defroute "*" []
;                    (set-html! application "<h1>This is slightly wrong. Fixme: Display nice page here.</h1>"))


; Dummy, to touch the file
(defn init-routes [])

(defn current-page []
  (session/get :current-page :dashboard-page))

;(defn selected-item-id []
;  (session/get :selected-item-id))


;;; Media breakpoints as defined in bootstrap (PX)
(def media-breakpoints {
                        :xs 0
                        :sm 544  ; Small screen / phone
                        :md 768  ; Medium screen / tablet
                        :lg 992  ;Large screen / desktop
                        :xl 1200 ; // Extra large screen / wide desktop
                        })
