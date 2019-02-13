(ns numerix.lib.roles
  (:require [agynamix.roles :as r]))

(def role-admin-all         "admin/all")
(def role-user-all          "user/all")
(def role-project-admin-all "project-admin/all")

(def role-project-all       "project/all")
(def role-project-access    "project/access")
(def role-project-read-only "project/read")
(def role-project-edit-own  "project/edit-own")

(def role-contacts-all            "contacts/all")
(def role-contacts-access         "contacts/access")
(def role-contacts-read-only      "contacts/read")
(def role-contacts-edit-own       "contacts/edit-own")
(def role-timeroll-all            "timeroll/all")
(def role-timeroll-access         "timeroll/access")
(def role-timeroll-read-only      "timeroll/read")
(def role-timeroll-edit-own       "timeroll/edit-own")
(def role-invoices-all            "invoices/all")
(def role-invoices-access         "invoices/access")
(def role-invoices-read-only      "invoices/read")
(def role-invoices-edit-own       "invoices/edit-own")
(def role-chat-room-all           "chat-room/all")
(def role-chat-room-access        "chat-room/access")
(def role-chat-room-read-only     "chat-room/read")
(def role-chat-room-edit-own      "chat-room/edit-own")
(def role-meetings-all            "meetings/all")
(def role-meetings-access         "meetings/access")
(def role-meetings-read-only      "meetings/read")
(def role-meetings-edit-own       "meetings/edit-own")
(def role-documents-all           "documents/all")
(def role-documents-access        "documents/access")
(def role-documents-read-only     "documents/read")
(def role-documents-edit-own      "documents/edit-own")
(def role-knowledgebase-all       "knowledgebase/all")
(def role-knowledgebase-access    "knowledgebase/access")
(def role-knowledgebase-read-only "knowledgebase/read")
(def role-knowledgebase-edit-own  "knowledgebase/edit-own")

;; Definition of roles
;; - access: user has access to the domain, but won't see any items except specifically given access to
;; - read: user has access to domain and can read all items, but cannot create any of his own
;; - edit-own: user has access to domain and can read all items and can create/edit/delete own items
;; - edit: user has full create/edit/delete access to a domain

(def all-domains [:contacts :timeroll :invoices :documents :knowledgebase])

(def role-map {
               role-user-all            #{"user:*"}
               role-admin-all           #{"admin:*"}

               role-contacts-all        #{"contacts:*"}
               role-contacts-access     #{"contacts:access:*"}
               role-contacts-read-only  #{role-contacts-access "contacts:read:*"}
               role-contacts-edit-own   #{role-contacts-read-only "contacts:edit-own:*"}
               role-timeroll-all        #{"timeroll:*"}
               role-timeroll-access     #{"timeroll:access:*"}
               role-timeroll-read-only  #{role-timeroll-access "timeroll:read:*"}
               role-timeroll-edit-own   #{role-timeroll-read-only "timeroll:edit-own:*"}
               role-invoices-all        #{"invoices:*"}
               role-invoices-access     #{"invoices:access:*"}
               role-invoices-read-only  #{role-invoices-access "invoices:read:*"}
               role-invoices-edit-own   #{role-invoices-read-only "invoices:edit-own:*"}
               role-chat-room-all       #{"chat-room:*"}
               role-chat-room-access    #{"chat-room:access:*"}
               role-chat-room-read-only #{role-chat-room-access "chat-room:read:*"}
               role-chat-room-edit-own  #{role-chat-room-read-only "chat-room:edit-own:*"}
               role-meetings-all        #{"meetings:*"}
               role-meetings-access     #{"meetings:access:*"}
               role-meetings-read-only  #{role-meetings-access "meetings:read:*"}
               role-meetings-edit-own   #{role-meetings-read-only "meetings:edit-own:*"}
               role-documents-all       #{"documents:*"}
               role-documents-access    #{"documents:access:*"}
               role-documents-read-only #{role-documents-access "documents:read:*"}
               role-documents-edit-own  #{role-documents-read-only "documents:edit-own:*"}
               role-knowledgebase-all       #{"knowledgebase:*"}
               role-knowledgebase-access    #{"knowledgebase:access:*"}
               role-knowledgebase-read-only #{role-knowledgebase-access "knowledgebase:read:*"}
               role-knowledgebase-edit-own  #{role-knowledgebase-read-only "knowledgebase:edit-own:*"}

               role-project-admin-all  #{"project-admin:*" "memberships:*"}
               role-project-all        #{role-contacts-all role-timeroll-all role-invoices-all role-documents-all role-chat-room-all role-meetings-all role-knowledgebase-all "project:*"}
               role-project-access     #{role-contacts-access role-timeroll-access role-invoices-access role-documents-access role-chat-room-access role-meetings-access role-knowledgebase-access "project:access:*"}
               role-project-read-only  #{role-contacts-read-only role-timeroll-read-only role-invoices-read-only role-documents-read-only role-chat-room-read-only role-meetings-read-only role-knowledgebase-read-only "project:access:*" "project:read:*"}
               role-project-edit-own   #{role-contacts-edit-own role-timeroll-edit-own role-invoices-edit-own role-documents-edit-own role-chat-room-edit-own role-meetings-edit-own role-knowledgebase-edit-own "project:access:*" "project:read:*" "project:edit-own:*"}

               })


(defn roles-to-keywords [roles]
  (into #{} (mapv (fn [name] (if (keyword? name)
                               name
                               (keyword name))) roles)))

;[{:id "Cat A"
;  :type "foo"
;  :title "My first category",
;  :items [{:_id "1a" :label "V a"} {:_id "2a" :label "V b"} {:_id "3a" :label "V c"}],
;  :single false}
; {:id "Category B"
;  :type "bar"
;  :title "My second category",
;  :items [{:_id "1b" :label "Value 1"} {:_id "2b" :label "Value 2"} {:_id "3b" :label "Value 3"}],
;  :single false}
; ]

(defn list-available-roles-for-select []
  [
   {:id "project"
    :type "project"
    :title "All Project Areas",
    :items [
            {:_id role-project-access :label role-project-access}
            {:_id role-project-read-only :label role-project-read-only}
            {:_id role-project-edit-own :label role-project-edit-own}
            {:_id role-project-all :label role-project-all}
            ],
    :single false
    }
   {:id "contacts"
    :type "contacts"
    :title "Addressbook",
    :items [
            {:_id role-contacts-access :label role-contacts-access}
            {:_id role-contacts-read-only :label role-contacts-read-only}
            {:_id role-contacts-edit-own :label role-contacts-edit-own}
            {:_id role-contacts-all :label role-contacts-all}
            ],
    :single false
    }
   {:id "timeroll"
    :type "timeroll"
    :title "Timeroll",
    :items [
            {:_id role-timeroll-access :label role-timeroll-access}
            {:_id role-timeroll-read-only :label role-timeroll-read-only}
            {:_id role-timeroll-edit-own :label role-timeroll-edit-own}
            {:_id role-timeroll-all :label role-timeroll-all}
            ],
    :single false
    }
   {:id "invoices"
    :type "invoices"
    :title "Invoices",
    :items [
            {:_id role-invoices-access :label role-invoices-access}
            {:_id role-invoices-read-only :label role-invoices-read-only}
            {:_id role-invoices-edit-own :label role-invoices-edit-own}
            {:_id role-invoices-all :label role-invoices-all}
            ],
    :single false
    }
   {:id "chat"
    :type "chat"
    :title "Chat",
    :items [
            {:_id role-chat-room-access :label role-chat-room-access}
            {:_id role-chat-room-read-only :label role-chat-room-read-only}
            {:_id role-chat-room-edit-own :label role-chat-room-edit-own}
            {:_id role-chat-room-all :label role-chat-room-all}
            ],
    :single false
    }
   {:id "meetings"
    :type "meetings"
    :title "Meetings",
    :items [
            {:_id role-meetings-access :label role-meetings-access}
            {:_id role-meetings-read-only :label role-meetings-read-only}
            {:_id role-meetings-edit-own :label role-meetings-edit-own}
            {:_id role-meetings-all :label role-meetings-all}
            ],
    :single false
    }
   {:id "documents"
    :type "documents"
    :title "Documents",
    :items [
            {:_id role-documents-access :label role-documents-access}
            {:_id role-documents-read-only :label role-documents-read-only}
            {:_id role-documents-edit-own :label role-documents-edit-own}
            {:_id role-documents-all :label role-documents-all}
            ],
    :single false
    }
   {:id "knowledgebase"
    :type "knowledgebase"
    :title "Knowledgebase",
    :items [
            {:_id role-knowledgebase-access :label role-knowledgebase-access}
            {:_id role-knowledgebase-read-only :label role-knowledgebase-read-only}
            {:_id role-knowledgebase-edit-own :label role-knowledgebase-edit-own}
            {:_id role-knowledgebase-all :label role-knowledgebase-all}
            ],
    :single false
    }
   ]

  )


(defn initialize-roles
  "Initializes the permission system with the set of defined roles"
  []
  (r/init-roles role-map))