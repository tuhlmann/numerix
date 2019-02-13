(ns numerix.routes
  (:require [taoensso.timbre :as log]
            [numerix.views.dashboard]
            [numerix.views.settings]
            [numerix.views.contact]
            [numerix.views.contacts]
            [numerix.views.invoices]
            [numerix.views.chat-rooms]
            [numerix.views.meetings]
            [numerix.views.calendar]
            [numerix.views.documents]
            [numerix.views.timeroll]
            [numerix.views.projects]
            [numerix.views.memberships]
            [numerix.views.imprint]
            [numerix.views.textblocks]
            [numerix.views.knowledgebase]
            [numerix.views.admin.users-page]
            [reagent.session :as session]))

(defn get-page [page]
  (condp = page
    :dashboard       #'numerix.views.dashboard/dashboard-page
    :imprint         #'numerix.views.imprint/imprint-page
    :contact         #'numerix.views.contact/contact-page
    :password        #'numerix.views.settings/password-page
    :settings        #'numerix.views.settings/settings-page
    :projects        #'numerix.views.projects/projects-page
    :memberships     #'numerix.views.memberships/memberships-page
    :contacts        #'numerix.views.contacts/contacts-page
    :invoices        #'numerix.views.invoices/invoices-page
    :chat-room       #'numerix.views.chat-rooms/chat-room-page
    :meetings        #'numerix.views.meetings/meetings-page
    :calendar        #'numerix.views.calendar/calendar-page
    :documents       #'numerix.views.documents/documents-page
    :timeroll        #'numerix.views.timeroll/timeroll-page
    :textblocks      #'numerix.views.textblocks/textblocks-page
    :knowledgebase   #'numerix.views.knowledgebase/knowledgebase-page

    :admin-users     #'numerix.views.admin.users-page/admin-users-page

    ;; Default
    #'numerix.views.dashboard/dashboard-page
  ))
