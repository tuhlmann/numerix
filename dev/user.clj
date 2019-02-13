(ns user
  (:require [numerix.main]
            [ring.middleware.reload :refer [wrap-reload]]
            [figwheel-sidecar.repl-api :as figwheel]
            [asset-minifier.core :as minifier]
            [clojure-watch.core :refer [start-watch]]
            [clojure.core.async :as a]
            [net.cgrand.reload :refer [auto-reload]]
            [taoensso.timbre :as log]
            [leiningen.core.main :as lein]
            [clojure.java.shell]
            [schema.core :as s]))


;; Let Clojure warn you when it needs to reflect on types, or when it does math
;; on unboxed numbers. In both cases you should add type annotations to prevent
;; degraded performance.
(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def browser-repl figwheel/cljs-repl)

(defn start-scss []
  (future
    (log/info "Starting scss.")
    (lein/-main ["sass" "auto"])))

#_(defn start-less []
   (future
     (Thread/sleep 1000)
     (println "Starting less.")
     (lein/-main ["less" "auto"])))

(defn assets-config []
  (->> "project.clj"
       slurp
       read-string
       (drop-while #(not= :minify-assets %))
       second))

(defn run-minify []
  (let [cfg (assets-config)]

    (future
      (Thread/sleep 800)
      (log/info "Minifying assets.")
      (doseq [[asset-type opts] cfg]
        (let [minify-fn (minifier/get-minifier-fn-by-type asset-type)]
          (minify-fn (:source opts) (:target opts) (:opts opts)))))))

#_(defn run-minify []
   (future
     (log/info "Starting Minifying assets.")
     (lein/-main ["minify-assets"])
     (lein/-main ["minify-assets" "watch"])))


#_(defn start-figwheel []
   (future
     (Thread/sleep 1200)
     (print "Starting figwheel.\n")
     (lein/-main ["figwheel"])))

(defn run-auto-reload []
  ;(auto-reload *ns*) ;This is the reload for html templates
  (auto-reload 'numerix.views.landing) ;This is the reload for html templates
  (run-minify)
  (start-scss)
  (figwheel/start-figwheel!)
  )

(defn run []
  (log/info "start dev system")
  (run-auto-reload)
  (numerix.main/run-application))


(defn start []
  (log/info "start system")
  (numerix.main/start))

(defn stop []
  (numerix.main/stop))

(defn reset []
  (numerix.main/reset))


; (def base-dir "/home/tuhlmann/entw/aktuell/numerix/")
; (def scss-dir (str base-dir "src/scss/"))
; (def css-dir (str base-dir "resources/public/css/"))
; (def scss-file "style")
;
; (defn watch-sass [callback-fn]
;   (start-watch [{:path scss-dir
;                  :event-types [:create :modify :delete]
;                  :bootstrap (fn [path] (println "Starting to watch " path))
;                  :callback callback-fn
;                  :options {:recursive true}}]))
;
;
; (defn start-scss []
;   (future
;     (println "Start Sass Watcher")
;     (watch-sass (fn [event file]
;       (println "File changed " file)
;       (println "Command: " "/usr/local/bin/sassc "
;           (str scss-dir scss-file ".scss")
;           " > "
;           (str css-dir scss-file ".css"))
;
;       (sh "/usr/local/bin/sassc"
;           (str scss-dir scss-file ".scss")
;           ">"
;           (str css-dir scss-file ".css"))))))


