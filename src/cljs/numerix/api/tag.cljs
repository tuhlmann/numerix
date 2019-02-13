(ns numerix.api.tag
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [reagent.core :as r]))

 (defn ws-list-user-tags [success-fn]
  (socket/send! [:tag/list-for-user] 5000
                (fn [response]
                  (success-fn (last response)))))

 (defn ws-list-project-tags [success-fn]
  (socket/send! [:tag/list-for-project] 5000
                (fn [response]
                  (success-fn (last response)))))

 (defn ws-list-roles [success-fn]
  (socket/send! [:project/roles-list] 5000
                (fn [response]
                  ;(log/info "log roles response " response)
                  (success-fn (last response)))))

(def tag-category {:id "Tags"
                   :type "tag"
                   :title "Tags"
                   :items []
                   })


(defn list-user-tags []
  (let [categories (r/atom nil)]
    (ws-list-user-tags
      (fn [response]
        (reset! categories [(assoc tag-category :items (map :label (get-in response [:data :result])))])))

    categories))

(defn list-project-tags []
  (let [categories (r/atom nil)]
    (ws-list-project-tags
      (fn [response]
        (reset! categories [(assoc tag-category :items (map :label (get-in response [:data :result])))])))

    categories))

(defn list-user-roles []
  (let [roles (r/atom nil)]
    (ws-list-roles
      (fn [response]
        (reset! roles (get-in response [:data :result]))))

    roles))

