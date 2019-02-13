(ns numerix.events.cache
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf :refer [dispatch]]
            [numerix.state :as s]
            [numerix.ws :as socket]
            [numerix.api.admin :as admin-api]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d]
            [numerix.views.common-controls :as ctrl]
            [numerix.api.cache :as cache]
            [numerix.views.common :as c]
            [numerix.site :as site]
            [clojure.string :as str]
            [numerix.api.crud-ops :as crud-ops]))


;; Used to load data from the server and cache it locally
(rf/reg-fx
  :load-and-cache
  (fn [[fn-id max-items]]
    (case fn-id
      :admin/user-list
      (cache/cache-admin-users max-items
                               (fn [i]
                                 (dispatch [:admin/load-user-list-success @i])))

      :knowledgebase/list
      (cache/cache-knowledgebase max-items
                                 (fn [i]
                                   (dispatch [:knowledgebase/load-list-success @i])))

      :timeroll/list
      (cache/cache-timeroll max-items
                            (fn [i]
                              (dispatch [:timeroll/load-list-success @i])))

      :contacts/list
      (cache/cache-contacts (fn [i]
                              (dispatch [:addressbook/load-list-success @i])))

      :chat-room/list
      (cache/cache-chat-rooms max-items
                             (fn [i]
                               ;(log/info "cache-chat-rooms " (pr-str @i))
                               (dispatch [:chat-room/load-list-success @i])))

      :meetings/list
      (cache/cache-meetings max-items
                             (fn [i]
                               ;(log/info "cache-meetings " (pr-str @i))
                               (dispatch [:meetings/load-list-success @i])))

      :documents/list
      (cache/cache-documents max-items
                             (fn [i]
                               ;(log/info "cache-documents " (pr-str @i))
                               (dispatch [:documents/load-list-success @i])))

      :invoices/list
      (cache/cache-invoices (fn [i]
                              ;(log/info "cache-invoices " (pr-str @i))
                              (dispatch [:invoices/load-list-success @i])))

      :cal-items/list
      (cache/cache-cal-items max-items
                             (fn [i]
                               (log/info "cache-cal-items " (pr-str @i))
                               (dispatch [:cal-items/load-list-success @i])))

      )))
