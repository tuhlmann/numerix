(ns numerix.api.meeting
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [numerix.views.common :as c]
            [numerix.api.crud-ops :as crud-ops]))


(defn list-meetings [max success-fn]
  (socket/send! [:meeting/list max] 5000
                (fn [response]
                  ;(log/info "list meetings result " (pr-str response))
                  (success-fn (last response)))))


