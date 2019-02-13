(ns numerix.api.knowledgebase
  (:require [taoensso.timbre      :as log]
            [numerix.ws           :as socket]
            [numerix.state        :as s]
            [cljs-time.core       :refer [now local-date-time days minus day-of-week date-time]]
            [cljs-time.format     :refer [formatter formatters parse unparse]]))

(defn list-knowledge-entries [limit success-fn]
  (socket/send! [:knowledgebase/list limit] 5000
                (fn [response]
                  ;(log/info "list companies result " (pr-str response))
                  (success-fn (last response)))))

