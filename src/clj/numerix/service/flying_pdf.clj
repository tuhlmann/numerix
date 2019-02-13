(ns numerix.service.flying-pdf
  (:require [taoensso.timbre        :as log]
            [stencil.core           :as stencil]
            [stencil.loader]
            [clojure.core.cache]
            [numerix.model.document :as document]
            [numerix.config         :as cfg])
  (:import (java.io File FileOutputStream ByteArrayInputStream ByteArrayOutputStream)
           (org.xhtmlrenderer.resource XMLResource)
           (org.xhtmlrenderer.pdf ITextRenderer)))

;; In development mode we don't cache the templates
(when (cfg/dev-mode?)
  (stencil.loader/set-cache (clojure.core.cache/ttl-cache-factory {} :ttl 0)))

(def invoice-styles "public/css/printable-styles.css")
(def invoice-default "reports/invoice-default.xhtml")

(defn render-pdf
  "Renders the template into a pdf document.
  Returns the document in memory for later processing"
  [document-params]

  (let [;css (stencil/render-file invoice-styles {})
        data (merge document-params {;:css-styles css
                                     :host-url cfg/host})

        template (stencil/render-file invoice-default data)
        document (.getDocument (XMLResource/load (ByteArrayInputStream. (.getBytes template))))
        os (ByteArrayOutputStream.)
        ;filename "invoice_rendered.pdf"
        ;os (FileOutputStream. filename)
        ;tplfile "invoice_rendered.xhtml"
        ;tpl-os (FileOutputStream. tplfile)

        renderer (ITextRenderer.)]

    (try

      (doto renderer
        (.setDocument document nil)
        (.layout)
        (.createPDF os))

      (.close os)

      ;(.write tpl-os (.getBytes template))
      ;(.close tpl-os)

      ;(document/create-document user-id (.toByteArray os))

      (.toByteArray os)

      (catch Exception e
        (log/error e)
        (.close os)

        nil))))
