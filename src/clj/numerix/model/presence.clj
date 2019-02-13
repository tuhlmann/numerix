(ns numerix.model.presence
  (:require [numerix.db :as db]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [numerix.config :as config]
            [numerix.lib.messages :refer [event-msg-handler]]
            [taoensso.timbre :as log]
            [clojure.pprint :as pprint]
            [numerix.config :refer [C]]
            [numerix.lib.helpers :as h]
            [numerix.model.user-db :as user-db]
            [taoensso.encore :as enc]
            [numerix.components.ws :as ws]
            [clj-time.core :as t]
            [clj-time.periodic :as periodic]
            [clojure.data :as data])
  (:import (org.bson.types ObjectId)))

(def presence-coll (:presence db/coll))

(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'timerolls'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (let [db (db/get-db)]
    (when-not (mc/exists? db presence-coll)
      (mc/create db presence-coll {})
      (mc/ensure-index db presence-coll (array-map :user-id 1) { :unique false })
      (mc/ensure-index db presence-coll (array-map :presence-id 1) { :unique false })
      (mc/ensure-index db presence-coll (array-map :ts 1) { :unique false })
      (mc/ensure-index db presence-coll (array-map :project-id 1) { :unique false })
      )))


;; Low level, DB access functions

(defn get-presence [id]
  (enc/when-let [_ id
                 db (db/get-db)]
                (mc/find-map-by-id db presence-coll id)))

(defn get-presence-by-presence-id [presence-id]
  (enc/when-let [_ presence-id
                 db (db/get-db)]
                (mc/find-one-as-map db presence-coll {:presence-id presence-id})))

(defn create-presence [presence-id tiny-user]
  (let [db (db/get-db)
        presence {:user-id (:_id tiny-user)
                  :project-id (:current-project tiny-user)
                  :presence-id presence-id
                  :email (:email tiny-user)
                  :ts (t/now)}]

    (mc/insert-and-return db presence-coll presence)))

(defn get-or-create-presence [presence-id tiny-user]
  (let [db (db/get-db)
        presence (first (mc/find-maps db presence-coll {:presence-id presence-id}))]

    (if presence
      presence
      (create-presence presence-id tiny-user))))


;(defn get-presence-by-user-id [user-id]
;  (enc/when-let [_ user-id
;                 db (db/get-db)]
;                (mc/find-one-as-map db presence-coll {:user-id user-id})))

;(defn get-existing-presence [presence-rec]
;  (enc/when-let [db (db/get-db)]
;                (mc/find-one-as-map db presence-coll {:user-id (:user-id presence-rec)
;                                                      :presence-id (:presence-id presence-rec)})))

(defn list-aged-records [max-minutes]
  (let [db (db/get-db)
        cmp-time (t/minus (t/now) max-minutes)
        result (mc/find-maps db presence-coll
                             {:ts {$lt cmp-time}})]
    result))

(defn list-connected-by-page-and-item [connected-uids related-type related-id]
  (let [db (db/get-db)
        result (mc/find-maps db presence-coll
                             {:related-type related-type
                              :related-id related-id})]

    (filterv #(contains? connected-uids (:presence-id %)) result)))

(defn list-connected-identities [form-name selected-item-id]
  (let [db (db/get-db)
        result (mc/find-maps db presence-coll
                             {:form-name form-name
                              :selected-item-id selected-item-id} [:identity :client-id])
        result (distinct (mapv :identity result))]

    ;(log/info "found connected identities: " result)
    result))

(defn list-connected-by-project
  "Find all presences for this project."
  ([project-id]
   (list-connected-by-project project-id (:any @(:connected-uids (C :ws-connection)))))

  ([project-id connected-uids]
  (let [db (db/get-db)
        result (into [] (mc/find-maps db presence-coll
                             {:project-id project-id}))]

    ;(log/info "found connected identities: " result)
    filterv #(contains? connected-uids (:presence-id %)) result)))

(defn list-connected-by-user-id
  "Find all presences for this user-id"
  ([user-id]
   (list-connected-by-user-id user-id (:any @(:connected-uids (C :ws-connection)))))

  ([user-id connected-uids]
  (let [db (db/get-db)
        result (into [] (mc/find-maps db presence-coll
                                      {:user-id user-id}))]

    ;(log/info "found connected identities: " result)
    filterv #(contains? connected-uids (:presence-id %)) result)))


;(defn update-presence [presence-rec]
;  (let [db (db/get-db)
;        existing-rec (get-existing-presence presence-rec)]
;    (if existing-rec
;      (do
;        (mc/update-by-id db presence-coll (:_id existing-rec)
;                         {"$set" {:ts (t/now)
;                                  :form-name (:form-name presence-rec)
;                                  :user-name (:user-name presence-rec)
;                                  :selected-item-id (:selected-item-id presence-rec)
;                                  :project-id (:project-id presence-rec)
;                                  }}
;                         {:multi false})
;        (get-presence (:_id existing-rec)))
;      (mc/insert-and-return db presence-coll presence-rec))))


(defn update-presence-location [presence presence-data]
  (let [db (db/get-db)
        upd {:ts (t/now)
             :related-type (:related-type presence-data)
             :related-id (:related-id presence-data)}]

    (mc/update-by-id db presence-coll (:_id presence)
                     {$set upd})

    (merge presence upd)))

(defn update-presence-ts [id]
  (let [db (db/get-db)]
    (mc/update-by-id db presence-coll id
                     {$set {:ts (t/now)}})))

(defn remove-presence [id]
  (let [db (db/get-db)]
    (mc/remove-by-id db presence-coll id)))

;(defn remove-presence-by-identity-and-client [identity client-id]
;  (let [db (db/get-db)]
;    (log/info "remove presence for identity / client-id " identity " / " client-id)
;    (mc/remove db presence-coll {:identity identity
;                                 :client-id client-id})))

(defn remove-expired
  "Triggered by the presence observer. Will remove records that have not been renewed for 'max-age'"
  [max-age]
  (let [expired-records (list-aged-records max-age)
        expired-by-project (into [] (group-by :project-id expired-records))]
    (doall (for [rec expired-records]
      (remove-presence (:_id rec))))
    ;; group by :project-id
    (log/info "expired by project " expired-by-project)
    (doall
      (for [[project-id recs] expired-by-project]
        (do
          (log/info "project-id " project-id)
          (log/info "expired recs " recs)
          (enc/when-let [connected-uids (:any @(:connected-uids (C :ws-connection)))
                         connected-by-project (not-empty (list-connected-by-project project-id))]

                        (ws/send-presence-list!
                          (C :ws-connection)
                          (map :presence-id connected-by-project)
                          [:presence/user-is-offline recs])

                        ))))))

(defn renew-connected
  "Triggered by the presence observer. Will renew records whose presence-id is still connected"
  [expiring-wait]
  (let [connected-uids (:any @(:connected-uids (C :ws-connection)))
        aged-records (list-aged-records expiring-wait)
        still-connected (filterv (fn [presence]
                                   (contains? connected-uids (:presence-id presence))) aged-records)]

    (doall (for [rec still-connected]
             (update-presence-ts (:_id rec))))))


(defn presence-connected [presence-list tiny-user]
  (doall (for [presence-id presence-list]
           (enc/when-let [connected-uids (:any @(:connected-uids (C :ws-connection)))
                          presence (get-or-create-presence presence-id tiny-user)
                          connected-presences (not-empty (list-connected-by-project (:project-id presence)))]

                         (ws/send-presence-list!
                           (C :ws-connection)
                           (map :presence-id connected-presences)
                           [:presence/user-is-online presence])))))


(defn presence-disconnected
  "A user disconnected from the server. Remove the presence record and publish
  to all connected presences of the same project"
  [presence-id]

  (enc/when-let [connected-uids (:any @(:connected-uids (C :ws-connection)))
                 presence (get-presence-by-presence-id presence-id)
                 connected-presences (not-empty (list-connected-by-project (:project-id presence)))]

                (remove-presence (:_id presence))

                (ws/send-presence-list!
                  (C :ws-connection)
                  (map :presence-id connected-presences)
                  [:presence/user-is-offline presence])))


(defn connection-state-change [old-lst new-lst]
  (let [[removed new _] (data/diff old-lst new-lst)]
    ;(log/info "connection state change (removed / new): " removed " / " new)

    (doall (for [presence-id removed]
             (presence-disconnected presence-id)))

    (enc/when-let [_ (not-empty new)
                   presence-map (group-by config/get-identity-from-presence new)
                   project-and-email-lst (user-db/find-current-projects-by-emails (keys presence-map))]
                  (doall (for [prj-and-email project-and-email-lst]
                           (do
                             (presence-connected (get presence-map (:email prj-and-email)) prj-and-email)

                             ;(let [m (member/list-member-identities-for-project (:current-project prj-and-email) new-lst)
                             ;      identities (map :email m)]

                                 ;(ws/send-identity-list!
                                 ;  (C :ws-connection)
                                 ;  identities
                                 ;  [:presence/user-is-offline (:email prj-and-email)])

                                 ;(ws/send-identity-list!
                                 ;  (C :ws-connection)
                                 ;  identities
                                 ;  [:presence/user-is-online (:email prj-and-email)])

                                 ))));)

    ))


;;; WS Communication functions

(defmethod event-msg-handler :presence/log-user-presence
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info ":presence/log-user-presence " ?data)
  (enc/when-let [connected-uids (:any @(:connected-uids (C :ws-connection)))
                 presence-data {:related-type    (:related-type ?data)
                                :related-id (if (and (some? (:related-id ?data))
                                                  (ObjectId/isValid (:related-id ?data)))
                                           (ObjectId. (:related-id ?data)) nil)
                                }
                 _ (some? (:related-type presence-data))
                 presence-id (config/get-presence-id-from-req ring-req)
                 presence (get-presence-by-presence-id presence-id)]

                (let [upd-presence (update-presence-location presence presence-data)
                      connected-by-project (not-empty (list-connected-by-project (:project-id presence)))]

                  (ws/send-presence-list!
                    (C :ws-connection)
                    (map :presence-id connected-by-project)
                    [:presence/presence-update upd-presence])

                  )))


;(defmethod event-msg-handler :presence/heartbeat
;  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;  (enc/when-let [user (user-db/get-user-by-req ring-req)
;                 identity (config/get-identity-from-req ring-req)
;                 client-id (config/get-client-id-from-req ring-req)
;                 rec {:user-id (:_id user)
;                      :user-name (h/name-or-email user)
;                      :project-id (:current-project user)
;                      :identity identity
;                      :client-id client-id
;                      :ts (t/now)
;                      :form-name (:form-name ?data)
;                      :selected-item-id (:selected-item-id ?data)}]
;
;                (do
;                  (update-presence rec)
;                  (let [connected-by-project (list-connected-by-project (:current-project user))]
;                    (ws/send-identity-list!
;                      (C :ws-connection)
;                      connected-by-project
;                      [:presence/presence-update rec])
;
;                  ))))


