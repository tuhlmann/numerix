(ns numerix.views.common-controls
  (:require [numerix.state   :as s]
            [taoensso.encore :as enc]
            [taoensso.timbre :as log]
            [numerix.history :as history]))

(defn show-detail-area? [form-state]
  (some true? [(:show-details form-state)]))

(defn show-print-view? [form-state]
  (some true? [(:show-print-view form-state)]))

(defn remove-body-background []
  (.css (js/$ "html") "background" "none")
  (.css (js/$ "body") "background" "none"))

(defn add-body-background []
  (.css (js/$ "html") "background" "url(/img/pw_pattern.png) repeat")
  (.css (js/$ "body") "background" "url(/img/pw_pattern.png) repeat"))

(defn show-detail-area
  ([db]
   (show-detail-area db true))
  ([db b]
   (let [form-state (s/form-state db)
         ;; FIXME Simplify! like update-form-state ...
         new-db (-> db
                    (s/assoc-in-form-state :show-details b)
                    ;(s/assoc-in-form-state :detail-only-view b)
                    )]
     (enc/when-let [_ (not b)
                    master-config (s/master-config db)
                    link-fn (:master-list-link master-config)]
                   (history/navigate! (link-fn)))
     new-db)))


(defn show-details-view-item
  ([db]
   (show-details-view-item db true))
  ([db show-details]
   (let [new-fs (-> (s/form-state db)
                    (assoc :edit-item false)
                    (dissoc :add-new))
         new-db (s/assoc-form-state db new-fs)
         new-db (if-not (or show-details (:selected-item new-fs))
                  (show-detail-area new-db false)
                  new-db)]
     new-db)))

(defn show-details-edit-item
  ([db]
   (show-details-edit-item db true))
  ([db edit-item?]
   (let [form-state (s/form-state db)
         new-fs (assoc form-state :edit-item edit-item?)
         new-db (s/assoc-form-state db new-fs)
         new-db (show-detail-area new-db edit-item?)]
     new-db)))
