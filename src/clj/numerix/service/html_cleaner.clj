(ns numerix.service.html-cleaner
  (:require [taoensso.timbre  :as log]
            [taoensso.encore  :as enc]
            [clojure.string   :as str]
            [cuerdas.core     :as cue])
  (:import (org.jsoup.safety Whitelist Cleaner)
           (org.jsoup Jsoup)
           (org.jsoup.nodes Document$OutputSettings)))

(def valid-colors ["rgb(0, 0, 0);"       "rgb(255, 153, 0);"   "rgb(255, 255, 0);"   "rgb(0, 138, 0);"
                   "rgb(230, 0, 0);"     "rgb(0, 102, 204);"   "rgb(153, 51, 255);"  "rgb(255, 255, 255);"
                   "rgb(250, 204, 204);" "rgb(255, 235, 204);" "rgb(255, 255, 204);" "rgb(204, 232, 204);"
                   "rgb(204, 224, 245);" "rgb(235, 214, 255);" "rgb(187, 187, 187);" "rgb(240, 102, 102);"
                   "rgb(255, 194, 102);" "rgb(255, 255, 102);" "rgb(102, 185, 102);" "rgb(102, 163, 224);"
                   "rgb(194, 133, 255);" "rgb(136, 136, 136);" "rgb(161, 0, 0);"     "rgb(178, 107, 0);"
                   "rgb(178, 178, 0);"   "rgb(0, 97, 0);"      "rgb(0, 71, 178);"    "rgb(107, 36, 178);"
                   "rgb(68, 68, 68);"    "rgb(92, 0, 0);"      "rgb(102, 61, 0);"    "rgb(102, 102, 0);"
                   "rgb(0, 55, 0);"      "rgb(0, 41, 102);"    "rgb(61, 20, 102);"])

(def valid-styles {
                   :font-size ["12px;" "16px;" "19px;", "32px;"]
                   :font-family ["'Segoe UI', Roboto, sans-serif;" "'Noto Serif', Times, Georgia, serif;"
                                 "Monaco, 'Courier New', monospace;"]
                   :text-align ["left;" "center;" "right;" "justify;"]
                   :color valid-colors
                   :background-color valid-colors
                   })

(def whitelist (doto (Whitelist.)
                 (.addTags
                   (into-array
                     ["b", "br", "caption", "cite", "code", "col",
                      "colgroup", "dd", "div", "dl", "dt", "em", "h1", "h2", "h3", "h4", "h5", "h6",
                      "i", "li", "ol", "p", "pre", "small", "strike", "strong",
                      "sub", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "u",
                      "ul", "span", "hr", "blockquote", "s", "del", "ins", "section", "header"]))
                 ;; removed "img" "a
                 (.addAttributes "col" (into-array ["span", "width"]))
                 (.addAttributes "colgroup" (into-array ["span", "width"]))
                 (.addAttributes "ol" (into-array ["start", "type"]))
                 (.addAttributes "table" (into-array ["summary", "width", "align"]))
                 (.addAttributes "td" (into-array
                                   ["abbr", "axis", "colspan", "rowspan", "width"]))
                 (.addAttributes "th" (into-array
                                   ["abbr", "axis", "colspan", "rowspan", "scope", "width"]))
                 (.addAttributes "ul" (into-array ["type"]))
                 (.addAttributes "span" (into-array ["title"]))
                 (.addAttributes "a" (into-array ["href", "target", "rel"]))
                 (.addAttributes "img" (into-array ["src", "width", "height", "title", "draggable"]))
                 ;(.addAttributes ":all" (into-array ["id"]))
                 (.addAttributes ":all" (into-array ["class"]))
                 (.addAttributes ":all" (into-array ["style"]))
                 ; !! The style attribute NEEDS to be cleaned, to allow only recognized values
                 ))

;; FIXME For img and links, check allowed protocolls. only http? should be allowed

;; Rewrite style attribute, check for allowed keys and allowed values.
;; Always rewrite, so only our stuff is included
(defn rewrite-style-attr [document]
  (let [elems (.select document "[style]")]
    (doall (for [elem elems]
             (try
               (enc/if-lets [style (.attr elem "style")
                             k (first (.split style ":"))
                             v (last (.split style ":"))
                             k (str/trim k)
                             v (str/trim v)
                             allowed-values (get valid-styles (keyword k))
                             value-valid (some (partial = v) allowed-values)]

                            (.attr elem "style" (str k ":" v))

                            (.attr elem "style" ""))

               (catch Exception ex
                 (log/error "Exception occured cleaning style attribute " ex)
                 (.attr elem "style" "")))))))

(defn clean-html [html]
  (if-not (empty? html)
    (let [settings (doto (Document$OutputSettings.)
                     (.prettyPrint false)
                     (.indentAmount 0))
          cleaner (Cleaner. whitelist)
          document (doto (Jsoup/parseBodyFragment html "")
                     (rewrite-style-attr))
          clean (doto (.clean cleaner document)
                  (.outputSettings settings))
          _      (doto (.select clean "table")
                   (.addClass "table")
                   (.addClass "table-striped")
                   (.addClass "table-bordered")
                   (.addClass "wysiwyg-table"))

          doc (if (> (count (.children (.body clean))) 1)
                (str "<div>" (.html (.body clean)) "</div>")
                (.html (.body clean)))]

      ;(log/info "to clean: " html " \n\n cleaned: " doc)

      doc)

    ""
  ))