(ns numerix.validation.mime-types
  (:require [taoensso.timbre        :as log]))

(def mime-types {
                 :image {
                         :icon "fa fa-file-image-o"
                         :content-type
                               #{
                                 "image/bmp"
                                 "image/x-windows-bmp"
                                 "image/vnd.dwg"
                                 "image/gif"
                                 "image/x-icon"
                                 "image/jpeg"
                                 "image/pict"
                                 "image/png"
                                 "image/x-quicktime"
                                 "image/tiff"
                                 "image/x-tiff"
                                 "image/x-ms-bmp"
                                 "image/vnd.adobe.photoshop"
                                 }
                         }
                 :pdf    {
                          :icon "fa fa-file-pdf-o"
                          :content-type
                                #{
                                  "application/pdf"
                                  "application/x-pdf"
                                  "application/acrobat"
                                  "applications/vnd.pdf"
                                  "text/pdf"
                                  "text/x-pdf"
                                  }
                          }
                 :audio   {
                           :icon "fa fa-file-audio-o"
                           :content-type
                                 #{
                                   "audio/mp3"
                                   "audio/mpeg3"
                                   "audio/x-mpeg-3"
                                   "audio/mpeg"
                                   "audio/x-ms-wma"
                                   "audio/x-wav"
                                   "application/vnd.rn-realmedia"
                                   "audio/mp4a-latm"
                                   "audio/x-pn-realaudio"
                                   "audio/vnd.rn-realaudio"
                                   "audio/wav"
                                   "audio/x-m4a"
                                   }
                           }
                 :video    {
                            :icon "fa fa-file-video-o"
                            :content-type
                                  #{
                                    "video/mp4"
                                    "video/avi"
                                    "video/msvideo"
                                    "video/x-msvideo"
                                    "video/x-mpeg"
                                    "video/mpeg"
                                    "video/x-ms-wmv"
                                    "video/x-ms-asf"
                                    "video/quicktime"
                                    "video/mp4a-latm"
                                    }
                            }
                 :archive   {
                             :icon "fa fa-file-archive-o"
                             :content-type
                                   #{
                                     "application/zip"
                                     "application/x-7z-compressed"
                                     "application/x-zip-compressed"
                                     "application/x-zip"
                                     "application/octet-stream"
                                     "application/bzip2"
                                     "application/x-bz2"
                                     "application/x-bzip"
                                     "application/x-compressed"
                                     }
                             }
                 :word       {
                              :icon "fa fa-file-word-o"
                              :content-type
                                    #{
                                      "application/msword",
                                      "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                      "application/vnd.openxmlformats-officedocument.wordprocessingml.template"
                                      "application/vnd.ms-word.document.macroEnabled.12"
                                      }
                              }
                 :excel       {
                               :icon "fa fa-file-excel-o"
                               :content-type
                                     #{
                                       "application/vnd.ms-excel"
                                       "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                       "application/vnd.openxmlformats-officedocument.spreadsheetml.template"
                                       "application/vnd.ms-excel.sheet.macroEnabled.12"
                                       "application/vnd.ms-excel.template.macroEnabled.12"
                                       "application/vnd.ms-excel.addin.macroEnabled.12"
                                       "application/vnd.ms-excel.sheet.binary.macroEnabled.12"
                                       }
                               }
                 :powerpoint   {
                                :icon "fa fa-file-powerpoint-o"
                                :content-type
                                      #{
                                        "application/vnd.ms-powerpoint",
                                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                        "application/vnd.openxmlformats-officedocument.presentationml.template"
                                        "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
                                        "application/vnd.ms-powerpoint.addin.macroEnabled.12"
                                        "application/vnd.ms-powerpoint.template.macroEnabled.12"
                                        "application/vnd.ms-powerpoint.slideshow.macroEnabled.12"
                                        }
                                }
                 :text          {
                                 :icon "fa fa-file-text-o"
                                 :content-type #{
                                                 "text/csv"
                                                 "text/html"
                                                 "text/plain"
                                                 "text/xml"
                                                 "application/pkix-cert"
                                                 "application/x-x509-ca-cert"
                                                 }
                                 }
                 })

(def all-allowed-types
  (into #{} (apply concat (map :content-type (vals mime-types)))))

(defn type-to-icon [content-type]
  (if-let [mime-struct  (first (filter (fn [mime-struct]
                                         (contains? (:content-type mime-struct) content-type))
                                       (vals mime-types)))]

    [:i {:class (:icon mime-struct)}]

    [:span content-type]
    ))


;(defn type-to-icon [type]
;  (cond
;    (= "application/pdf" type)
;    [:i.fa.fa-file-pdf-o]
;
;    (= "text/plain" type)
;    [:i.fa.fa-file-text-o]
;
;    (re-matches #"image/.*" type)
;    [:i.fa.fa-file-image-o]
;
;    :else
;    [:span type]
;
;    ))


;(log/info "all mime types " (pr-str all-allowed-types))