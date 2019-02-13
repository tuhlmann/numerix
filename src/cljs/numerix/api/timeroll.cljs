(ns numerix.api.timeroll
  (:require [taoensso.timbre      :as log]
            [numerix.ws           :as socket]
            [numerix.state        :as s]
            [numerix.api.user     :as user-api]
            [numerix.views.common :as c]
            [cljs-time.core       :refer [now local-date-time days minus day-of-week date-time]]
            [cljs-time.format     :refer [formatter formatters parse unparse]]
            [numerix.lib.helpers  :as h]))

;;; *** API ***

(defn list-timeroll-entries [days-back success-fn]
  (socket/send! [:timeroll/list days-back] 5000
                (fn [response]
                  ;(log/info "list companies result " (pr-str response))
                  (success-fn (last response)))))

