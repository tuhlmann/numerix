(ns numerix.model.subscription
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.utils :refer [auth-let]]
            [numerix.model.tag :as tags]
            [numerix.service.html-cleaner :as html-cleaner]
            [numerix.model.project :as prj]
            [numerix.config :refer [C]]
            [numerix.lib.helpers :as h]
            [numerix.model.membership :as member]
            [numerix.model.user :as user]
            [numerix.model.user-db :as user-db]
            [numerix.model.project-db :as project-db]
            [numerix.auth.form-auth :as form-auth]
            [taoensso.encore :as enc]
            [numerix.components.ws :as ws]
            [numerix.model.presence :as presence]
            [clj-time.core :as t]
            [mongoruff.collection :as ruff]
            [monger.query :as mq]))

(def subscription-coll (:subscription db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db subscription-coll)
      (mc/create db subscription-coll {})
      (mc/ensure-index db subscription-coll (array-map :ts 1) { :unique false })
      )))

(def subscription-schema
  {
   :db (fn[] (db/get-db))
   :collection subscription-coll
   :update-keys h/list-keys-without-augments
   :after-read-list nil ;; executed on the whole list of results, can change the resulting element list
   :after-read nil
   :before-save nil
   :after-save nil ;; pass in saved record, can retrieve a new rec from db and return that one with new augments
   }
  )

;; Low level, DB access functions

(defn get-subscription [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db subscription-coll id)))

(defn get-subscription-by-user [user-id project-id sub-type sub-id]
  (let [db (db/get-db)
        result (mq/with-collection db subscription-coll
                                   (mq/find {:user-id user-id
                                             :project-id project-id
                                             :sub-type sub-type
                                             :sub-id sub-id}))]
        (first result)))


(defn list-expired-subscriptions [connected-uids]
  (let [db (db/get-db)]
    (mc/find-maps db subscription-coll {:presence-id {$nin connected-uids}} [:presence-id])))


(defn list-subscribers [project-id sub-type sub-id]
  (ruff/query-many
    subscription-schema
    (fn [db coll]
      (into [] (mq/with-collection db coll
                                   (mq/find {:project-id project-id
                                             :sub-type sub-type
                                             :sub-id sub-id})
                                   )))))



(defn update-subscription [subscription]
  (ruff/update subscription-schema subscription))

(defn remove-subscription [subscription-id]
  (let [db (db/get-db)]
    (mc/remove-by-id db subscription-coll subscription-id)))

(defn remove-expired []
  (let [connected-uids (:any @(:connected-uids (C :ws-connection)))
        db (db/get-db)]
    (mc/remove db subscription-coll {:presence-id {$nin connected-uids}})))

;;; WS Communication functions

(defmethod event-msg-handler :remote-subscription/subscribe
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info ":remote-subscription/subscribe " (pr-str ?data))
  (enc/when-let
    [user (user-db/get-user-by-req ring-req)
     presence-id (config/get-presence-id-from-req ring-req)
     membership (member/get-membership-by-user user)
     ;has-access? (form-auth/as-form-read? :knowledgebase membership)
     subscription (merge
                    ?data
                    {:user-id (:_id user)
                     :project-id (:current-project user)
                     :presence-id presence-id
                     :ts (t/now)
                     })

     upd-subscription (update-subscription subscription)
     ]

    (log/info "subscribed to new subscription " (pr-str upd-subscription))
    ))


(defmethod event-msg-handler :remote-subscription/cancel
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info ":remote-subscription/cancel")
  (enc/when-let
    [user (user-db/get-user-by-req ring-req)
     membership (member/get-membership-by-user user)
     ;has-access? (form-auth/as-form-read? :knowledgebase membership)
     sub-type (:sub-type ?data)
     sub-id (:sub-id ?data)
     subscription (get-subscription-by-user
                    (:_id user)
                    (:current-project user)
                    sub-type
                    sub-id)]

    (remove-subscription (:_id subscription))
    (log/info "cancel subscription")
    ))

