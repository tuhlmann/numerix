(ns numerix.lib.route-helpers
  )

(def page-map {
              :meetings {:multi-items "meetings"
                         :single-item "meeting"}
              })

(defn single-item-uri [page]
  (get-in page-map [(keyword page) :single-item])
  "meeting"
  )

(defn make-chat-route [{:keys [host related-type related-id chat-msg-id] :as params
                        :or {host ""}}]
  (str
    host
    "/"
    (single-item-uri related-type)
    "/"
    related-id
    ;"?msg-id="
    ;chat-msg-id
    ))