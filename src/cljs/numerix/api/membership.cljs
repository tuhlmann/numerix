(ns numerix.api.membership
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [re-frame.core :as rf]))

(defn invite-members [invitation success-fn]
  (socket/send! [:membership/invite invitation] 5000
                (fn [response]
                  ;(log/info "invitation response is " (pr-str response))
                  (if (socket/response-ok (last response))
                    (success-fn (last response))))))
