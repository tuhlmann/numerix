(ns numerix.api.contact
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [numerix.lib.helpers :as h]))

(defn list-contacts [success-fn]
  (socket/send! [:contact/list] 5000
                (fn [response]
                  ;(log/info "list companies result " (pr-str response))
                  (success-fn (last response)))))

