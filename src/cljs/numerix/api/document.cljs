(ns numerix.api.document
  (:require [taoensso.timbre :as log]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.api.user :as user-api]
            [numerix.views.common :as c]
            [numerix.api.crud-ops :as crud-ops]))


(defn list-documents [max success-fn]
  (socket/send! [:document/list max] 5000
                (fn [response]
                  ;(log/info "list documents result " (pr-str response))
                  (success-fn (last response)))))


(defn new-document-rec [user]
  (merge (crud-ops/new-record user)
         {
          :allow-form-upload true
          :allow-form-remove true
          :allow-remove true
          })
  )


