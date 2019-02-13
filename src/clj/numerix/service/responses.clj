(ns numerix.service.responses
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [success error restrict]]
            [monger.conversion :as mconv]
            [numerix.model.files :as files]
            [numerix.views.layout :as lay]
            [numerix.auth.user-auth :as uauth]
            [clojure.pprint :as pprint]
            [numerix.config :as config]
            [taoensso.encore :as enc]
            [numerix.lib.helpers :as h]
            [taoensso.timbre :as log]
            [numerix.model.document :as document]))

(defn not-authorized
  [request value]
  {:status 404
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body "Not authorized"})

(defn error-page [req value]
  {:status  404
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (lay/landing-base req
                              [:div.alert.alert-danger
                               [:p "There was a problem with your request."]])
   })



(defn render-not-found-page []
  (lay/landing-base {}
                    [:div.alert.alert-danger
                     [:p "I'm sorry we couldn't find the page you were looking for."]]))


(defn download-file-response [file-id inline? check-access? req]
  (enc/if-lets
    [user-id (config/get-auth-user-id-from-req req)
     file-rec (files/get-file file-id)
     file-meta (mconv/from-db-object (.getMetaData file-rec) true)
     user-has-access (if check-access?
                       (uauth/user-can-download user-id file-meta)
                       true)]

    {:status  200
     :headers {"Content-Type"        (.getContentType file-rec)
               "Content-Disposition" (if inline?
                                       "inline;"
                                       (str "attachment; filename=" (.getFilename file-rec)))
               }
     :body    (.getInputStream file-rec)}

    (error-page req nil)))


