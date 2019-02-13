(ns numerix.api.project
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [re-frame.core :as rf]))

(defn find-project
  ([project-id]
   (find-project project-id @(rf/subscribe [:projects])))
  ([project-id projects]
   (first (filter #(= (:_id %) project-id) projects))))

;(defn find-root-project [projects]
;  (first (filter :is-root-project projects)))

(defn current-project []
  (let [current-project-id (:current-project @(rf/subscribe [:current-user]))
        prj (find-project current-project-id @(rf/subscribe [:projects]))]

    (log/info "current prj id " (pr-str current-project-id))
    (log/info "found prj " (pr-str prj))

    prj
    ))


(defn activate-project [project success-fn]
  (socket/send! [:project/activate (:_id project)] 5000
                (fn [response]
                  ;(log/info "response is " (pr-str response))
                  (if (socket/response-ok (last response))
                    (success-fn (last response))))))


(defn new-project-rec [user]
  ;(log/info "Current user id " (user-api/user-id user))
  {:author-id (:_id user)
   :summary ""})