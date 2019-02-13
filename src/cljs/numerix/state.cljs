(ns numerix.state
  (:require [reagent.core :as r]
            [re-frame.db :as db]
            [taoensso.timbre :as log]
            [clojure.data :as cl-data]
            [validateur.validation :as v]
            [numerix.lib.helpers :as h]
            [re-frame.core :as rf]))

; The state atom consists of:
; - user
; - my-memberships
; - view-state: defines properties the user has set that effect the view (sidebadr state, etc.)
; - window-state: defines properties of the current browser environment
; - form-state: defines state of current form
; - form-config: defines configuration of current form
; - master-config
; - memberships
; - notifications
; - meetings
; - documents
; - invoices
; - projects
; - memberships: members of this current project
; - contacts
; - textblocks
; - timeroll
; - knowledgebase
;
; - alerts (a map with alert message information)
;

(defn app-db []
  db/app-db)

; Called in main when the app is initialized
(rf/reg-event-db              ;; sets up initial application state
  :initialize                 ;; usage:  (dispatch [:initialize])
  (fn [db _]                  ;; the two parameters are not important here, so use _
    (if (empty? db)
      {:title "Numerix"         ;; What it returns becomes the new application state
       :messages []
       :view-state {
                    :sidebar-toggle-cls ""
                    }
       :re-render-flip false}

      db
    )))

(defn unload-all-data [db]
  (dissoc db
          :user
          :memberships
          :meetings
          :documents
          :invoices
          :projects
          :contacts
          :textblocks
          :timeroll
          :knowledgebase))


(defn add-alert2 [db alert]
  (update db :alerts
          (fn [alerts]
            (let [mapped-alerts (map (fn [a]
                                       (let [diffed (cl-data/diff alert a)
                                             f (first diffed)
                                             s (second diffed)
                                             same (and (nil? f) (nil? s))]
                                         (if same
                                           [(merge a alert) true]
                                           [a false]))) alerts)
                  found-existing (reduce #(or %1 %2) false (mapv second mapped-alerts))]

              (if found-existing
                (do
                  ;(log/info "found existing " (pr-str mapped-alerts))
                  (mapv first mapped-alerts))

                (do
                  ;(log/info "not found " (pr-str mapped-alerts))
                  (conj alerts (assoc alert :alert-id (h/short-rand)))))))))


(defn reduce-alert-timeouts [db amount]
  (update db :alerts (fn [alerts]
                       (mapcat (fn [alert]
                                 (let [new-t (if (:timeout alert)
                                               (update alert :timeout #(- % amount))
                                               alert)]
                                   (if-not (:timeout new-t)
                                     [new-t]
                                     (if (> (:timeout new-t) 0) [new-t] nil)))) alerts))))

(defn add-info-alert-in [db msg]
  (add-alert2 db {:type :info :msg msg :timeout 5}))

(defn validation-errors
  ([form-state]
   (:errors form-state {}))
  ([form-state k]
   (let [errors (or (:errors form-state {}) {})]
     ;; if the key is there but nil it will return nil, that's why we wrap it in an or
     (v/errors k errors))))


(defn remove-all-alerts [db]
  (assoc db :alerts []))

(defn remove-alert [db alert-id]
  (update db :alerts (fn [alerts]
                       (filterv (fn [a] (not= alert-id (:alert-id a))) alerts))))


(defn form-state [db]
  (:form-state db))

(defn get-in-form-state [db k]
  (if (vector? k)
    (get-in db (into [:form-state] k))
    (get-in db [:form-state k])))

(defn get-in-form-data [db k]
  (if (vector? k)
    (get-in db (into [:form-config :form-data] k))
    (get-in db [:form-config :form-data k])))

(defn get-in-view-state [db k]
  (if (vector? k)
    (get-in db (into [:view-state] k))
    (get-in db [:view-state k])))

(defn get-in-post-config [db k]
  (if (vector? k)
    (get-in db (into [:post-config] k))
    (get-in db [:post-config k])))

(defn get-in-session [db k]
  (if (vector? k)
    (get-in db (into [:session] k))
    (get-in db [:session k])))

(defn assoc-in-session [db k v]
  (if (vector? k)
    (assoc-in db (into [:session] k) v)
    (assoc-in db [:session k] v)))

(defn dissoc-in-session [db k]
  (if (vector? k)
    (h/dissoc-in db (into [:session] k))
    (h/dissoc-in db [:session k])))

(defn master-config [db]
  (:master-config db))

(defn master-data [db]
  (get-in db [:master-config :master-data]))

(defn assoc-form-state [db form-state]
  (assoc db :form-state form-state))

(defn update-form-state [db f]
  (update db :form-state f))

(defn assoc-in-form-state [db k v]
  (if (vector? k)
    (assoc-in db (into [:form-state] k) v)
    (assoc-in db [:form-state k] v)))

(defn dissoc-in-form-state [db k]
  (if (vector? k)
    (h/dissoc-in db (into [:form-state] k))
    (h/dissoc-in db [:form-state k])))

(defn update-in-form-state [db k f]
  (if (vector? k)
    (update-in db (into [:form-state] k) f)
    (update-in db [:form-state k] f)))

(defn assoc-in-form-data [db k v]
  (if (vector? k)
    (assoc-in db (into [:form-config :form-data] k) v)
    (assoc-in db [:form-config :form-data k] v)))

(defn dissoc-in-form-data [db k]
  (if (vector? k)
    (h/dissoc-in db (into [:form-config :form-data] k))
    (h/dissoc-in db [:form-config :form-data k])))

(defn assoc-in-view-state [db k v]
  (if (vector? k)
    (assoc-in db (into [:view-state] k) v)
    (assoc-in db [:view-state k] v)))

(defn assoc-master-config [db master-config]
  (assoc db :master-config master-config))

(defn assoc-form-config [db form-config]
  (assoc db :form-config form-config))

(defn assoc-in-form-config [db k v]
  (if (vector? k)
    (assoc-in db (into [:form-config] k) v)
    (assoc-in db [:form-config k] v)))

(defn dissoc-in-form-config [db k]
  (if (vector? k)
    (h/dissoc-in db (into [:form-config] k))
    (h/dissoc-in db [:form-config k])))

(defn assoc-master-data [db master-data]
  (assoc-in db [:master-config :master-data] master-data))


(defn merge-form-data-error2 [db error-msg]
  (let [errors (get-in-form-state db :errors)]
    (assoc-in-form-state db :errors (merge errors error-msg))))

(defn assoc-user [db user]
  (assoc db :user user))

(defn assoc-in-user [db k v]
  (if (vector? k)
    (assoc-in db (into [:user] k) v)
    (assoc-in db [:user k] v)))

(defn dissoc-in-user [db k]
  (if (vector? k)
    (h/dissoc-in db (into [:user] k))
    (h/dissoc-in db [:user k])))

(defn add-form-state-auto-flag
  "assoc a value into the given atom that is removed automatically
  after the given timeout in seconds."
  [db path value timeout-s]
  (let [new-db (assoc-in-form-state db path value)]
    (js/setTimeout #(rf/dispatch [:dissoc-in-form-state path]) (* timeout-s 1000))

  new-db))

(defn open-document-in [db url]
  (assoc-in db [:form-state :open-document] url))

