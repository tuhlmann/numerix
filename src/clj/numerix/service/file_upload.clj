(ns numerix.service.file-upload
  (:require [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [clojure.string :as str]
            [cuerdas.core :as cue]
            [cheshire.core :refer [generate-string]]
            [clojure.pprint :as pprint]
            [numerix.config :as config]
            [numerix.model.user :as user]
            [numerix.model.document :as document]
            [numerix.model.files :as files]
            [numerix.service.responses :as resp]
            [numerix.validation.file-vali :as file-vali]
            [numerix.model.user-db :as user-db])
  (:import (org.bson.types ObjectId)))


(defn make-response [status value-map]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (generate-string value-map)})

(defn add-to-related-record [user-id related-type related-id results]
  (let [file-ids (->> results
                       (filter #(= (:status %) :OK))
                       (map :file-id)
                       (map #(ObjectId. %))
                       (into []))]

    (log/info "file-ids " (pr-str file-ids))

    (when (seq file-ids)

      (condp = related-type

        "document" (document/attach-files user-id related-id file-ids)

        (log/error (str "related-type " related-type " not implemented"))))
  ))

(defn remove-from-related-record [user-id related-type related-id remove-results]
  (let [file-ids (->> remove-results
                       (filter #(= (:status %) :OK))
                       (map :file-id)
                       (map #(ObjectId. %))
                       (into []))]

    (log/info "remove file-ids " (pr-str file-ids))
    (when (seq file-ids)

      (condp = related-type

        "document" (document/remove-files user-id related-id file-ids)

        (log/error (str "related-type " related-type " not implemented"))))
  ))

;; FIXME: Also check that related-id exists
(defn change-and-save-file [& {:keys [file related-id related-type author-id]}]
  (let [{:keys [filename size]} file]
    (log/info "process file " (pr-str file))
    ;(println (str "handle-upload: Filename: " (or filename "null") " size: "
    ;              (or size 0) " tempfile: " (str (or tempfile "null"))))
    (let [vali-map (file-vali/file-upload-validations file)
          _ (log/info "Vali map is " (pr-str vali-map))
          status (if (seq vali-map)
                   (do
                     (log/info "Errors: " (pr-str vali-map))
                     {:status :ERROR
                      :filename filename
                      :message (str (apply concat (vals vali-map)))}
                     )

                   {:status :OK
                    :filename filename
                    :size (or size 0)}

                   )]

      (if (= (:status status) :OK)
        (let [doc-meta {:filename filename
                        :metadata {
                                   :author-id author-id
                                   :related-id related-id
                                   :related-type related-type
                                   }
                        :content-type (:content-type file) }
              file-record (document/save-file (:tempfile file) doc-meta false)]

          (log/info "file record" (pr-str file-record))
          (merge status {:file-id (str (:_id file-record))}))

        status))))

(defn remove-file [& {:keys [file-id author-id]}]
  (log/info "remove file " (pr-str file-id))
  (enc/when-lets
    [_ (ObjectId/isValid file-id)
     file-id (ObjectId. file-id)
     file (files/get-file-info file-id)]

    (log/info "remove file " (pr-str file))
    (let [vali-map (file-vali/file-remove-validations {:file file :author-id author-id})
          _ (log/info "Vali map is " (pr-str vali-map))
          status (if (seq vali-map)
                   (do
                     (log/info "Errors: " (pr-str vali-map))
                     {:status :ERROR
                      :filename (:filename file)
                      :message (str (apply concat (vals vali-map)))}
                     )

                   {:status :OK
                    :filename (:filename file)}

                   )]

      (if (= (:status status) :OK)
        (let [result (files/remove-file-by-id file-id)]
          (merge status {:file-id (str file-id)}))

        status))))

;; FIXME: We need to check that the user has access to the related-id model.
(defn handle-upload [req]
  (enc/when-lets
    [user-id (config/get-auth-user-id-from-req req)
     related-id (get-in req [:params :related-id])
     related-id (ObjectId. related-id)
     related-type (get-in req [:params :related-type])]

    (let [files (vals (get-in req [:params :file]))
          files-to-remove (into [] (cue/split (get-in req [:params :files-to-remove]) ","))]

      (log/info "files to remove " (pr-str files-to-remove))
      ;(pprint/pprint (get-in req [:params :file]))

      (let [results
            (doall
              (map (fn [file]
                     (change-and-save-file :file file
                                           :author-id user-id
                                           :related-id related-id
                                           :related-type related-type )) files))

            remove-results
            (doall
              (map (fn [file-to-remove]
                     (remove-file :file-id file-to-remove :author-id user-id)) files-to-remove))
            ]

        (log/info "remove-results is " (pr-str remove-results))

        (if (or
              (empty? files)
              (reduce
                (fn [accu json]
                  (or accu (= (:status json) :OK))) false results))

          (do
            (log/info "add related record")
            (add-to-related-record user-id related-type related-id results)
            (remove-from-related-record user-id related-type related-id remove-results)
            (make-response 200 results))

          (make-response 400 results)))

    )))

(defn change-profile-image [req]
  (enc/when-lets
    [img (first (vals (get-in req [:params :file])))
     matches-mime-type (re-matches #"image/.*" (:content-type img))
     user-id (config/get-auth-user-id-from-req req)
     user    (user-db/get-user user-id)]

    (log/info "Image to upload " (pr-str img))

    (when-let [profile-image-id (:profile-image-id user)]
      (files/remove-file-by-id profile-image-id))

    (let [doc-meta {:filename "profile-image"
                    :metadata {
                               :author-id user-id
                               }
                    :content-type (:content-type img) }
          file-record (document/save-file (:tempfile img) doc-meta false)
          upd-user (assoc user :profile-image-id (:_id file-record))]

      (log/info "file record" (pr-str file-record))

      (user-db/update-user (:email user) upd-user)

      (make-response 200 [{:status "OK"
                           :profile-image-id (str (:_id file-record))}])

    )))

(defn get-profile-image [profile-image-id req]
  ;(log/info "get profile img " profile-image-id)
  (enc/when-let
    [user-id (config/get-auth-user-id-from-req req)
     user    (user-db/get-user user-id)
     ;profile-image-id (:profile-image-id user)
     ]

    (let [re (resp/download-file-response profile-image-id true false req)]
      ;(log/info "return profile img " re)
      re)))
