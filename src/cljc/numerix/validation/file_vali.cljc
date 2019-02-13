(ns numerix.validation.file-vali
  (:require
    [taoensso.timbre        :as log]
    [validateur.validation  :as v]
    [cuerdas.core           :as cue]
    [numerix.validation.mime-types :as mime]))

(def min-filename-length 3)
(def max-filename-length 1000)
(def max-file-size 104857600) ; 100MB
(def max-file-size-str "100MB")

(def file-upload-validations
  (v/validation-set

    (v/length-of :filename :within (range min-filename-length max-filename-length))
    (v/validate-with-predicate :size (fn [{:keys [size]}]
                                       (> size 10))
                               :message "File size less than 10 bytes is not supported.")
    (v/validate-with-predicate :size (fn [{:keys [size]}]
                                       (<= size max-file-size))
                               :message (str "Uploaded file too large. We currently support files to up to "
                                             max-file-size-str))
    (v/validate-with-predicate :content-type (fn [{:keys [content-type]}]
                                               (contains? mime/all-allowed-types content-type))
                               :message-fn (fn [{:keys [content-type]}]
                                             (str "Content type " content-type " is not supported."))


                               )))

(def file-remove-validations
  (v/validation-set

    (v/validate-with-predicate :file (fn [{:keys [file author-id]}]
                                       (= (get-in file [:metadata :author-id] author-id))
                               :message "The file does not belong to this user."))))

