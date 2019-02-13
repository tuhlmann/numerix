(ns numerix.components.pdf-renderer
  (:require
    [taoensso.timbre :as log]
    [taoensso.encore :as enc]
    [numerix.config :as cfg]
    [clojure.pprint :as pprint]
    [clojure.core.async :as a
     :refer [>! <! >!! <!! put! go go-loop chan buffer close! thread alts! alts!! timeout sliding-buffer]]
    [robur.events :refer [emit subscribe]]
    [com.stuartsierra.component :as component]
    [numerix.service.flying-pdf :as flying-pdf]
    [numerix.model.document :as document]))

(defrecord PdfRenderer [render-channel]
  component/Lifecycle

  (start [component]
    (if render-channel
      component

      (let [component (component/stop component)
            channel (chan (sliding-buffer 5000))]

        (log/info "PDF Renderer started")
        (go-loop []
          (when-let  [document (<! channel)]
            (when (cfg/dev-mode?)
              (log/info "New document to render")
              ;(pprint/pprint document)
              )
            (try
              (if-let [doc-bytes (flying-pdf/render-pdf document)]
                (do
                  ;(log/info "document rendered into memory.")
                  (when (fn? (:document-ready-fn document))
                    ((:document-ready-fn document) doc-bytes)))

                (log/error "Error rendering document: " (pr-str document)))
              (catch Exception e
                (log/error "Exception rendering document: " e)
                ))
            (recur)))
        (assoc component
          :render-channel channel))))

  (stop [component]
    (log/info "PDF Renderer stopped")
    (when-let [channel (:render-channel component)]
      (close! channel))
    (assoc component
      :render-channel nil)))

;; Constructor function
(defn new-pdf-renderer []
  (map->PdfRenderer {}))

(defn render-document [renderer document]
  (>!! (:render-channel renderer) document))


