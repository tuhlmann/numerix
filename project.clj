(defproject numerix "0.1.0-SNAPSHOT"
  :description "Numerix is Torsten's first Clojure project"
  :url "http://numerix.at"
  :license {:name "Undecided license"
            :url "http://www.I-dont-know.yet/license.txt"}

  :source-paths ["src/clj" "src/cljs" "src/cljc" "dev"]
  :test-paths   ["test/clj"]

  :dependencies [[org.clojure/clojure "1.9.0-beta2"]
                 [reagent "0.7.0" :exclusions [cljsjs/react]]
                 [cljsjs/react-with-addons "15.5.4-0"]
                 [reagent-utils "0.2.1"]
                 [re-frame "0.10.2"]
                 [cljsjs/jquery "2.2.4-0"]
                 [cljsjs/jquery-ui "1.11.4-0"]
                 [cljsjs/tether "1.4.0-0"]
                 [cljsjs/bootstrap "3.3.6-1"]
                 [cljsjs/moment "2.17.1-1"]
                 ;[cljsjs/quill "1.2.4-3"]
                 [cljsjs/react-select "1.0.0-beta14-0" :exclusions [cljsjs/react]]
                 [cljsjs/cropper "0.8.1-0"]
                 [secretary "1.2.3"]
                 [org.clojure/clojurescript "1.9.946"]
                 [ring/ring-defaults "0.3.1"]
                 [metosin/ring-http-response "0.9.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [prone "1.1.4"]
                 [compojure "1.6.0"]
                 [environ "1.1.0"]
                 [hiccup "1.0.5"]
                 [enlive "1.1.6"]
                 [com.akolov.enlive-reload "0.2.1"]
                 [org.clojure/core.cache "0.6.5"]
                 [org.clojure/core.match "0.2.2"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.analyzer.jvm "0.7.1"]
                 ;[com.cemerick/pomegranate "0.3.1"]
                 [buddy/buddy "2.0.0"]
                 [lib-noir "0.9.9"]
                 [com.novemberain/validateur "2.5.0"]       ; form data validation
                 [com.taoensso/sente "1.11.0"]               ; WebSocket communication
                 [com.taoensso/timbre "4.10.0"]              ; Logging
                 [com.taoensso/encore "2.92.0"]
                 [com.cognitect/transit-clj  "0.8.300"]
                 [com.cognitect/transit-cljs "0.8.243"]
                 [com.novemberain/monger "3.1.0"]
                 [http-kit "2.2.0"]
                 [riddley "0.1.14"]
                 [prismatic/schema "1.1.7"]
                 [clj-time "0.14.0"]
                 [com.andrewmcveigh/cljs-time "0.5.1"]
                 [clojure-watch "0.1.13"]
                 [clojurewerkz/mailer "1.3.0"]
                 [dragonmark/util "0.1.4" :exclusions [org.clojure/clojure]]
                 [com.rpl/specter "1.0.4"]
                 [funcool/cats "2.1.0"]
                 [funcool/promesa "1.9.0"]
                 [funcool/cuerdas "2.0.4"]
                 [re-com "2.1.0" :exclusions [reagent/reagent com.andrewmcveigh/cljs-time]]
                 [com.fzakaria/slf4j-timbre "0.3.7"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.xhtmlrenderer/flying-saucer-pdf "9.1.7" :exclusions [bouncycastle/bcprov-jdk14]]
                 [stencil "0.5.0"]
                 [org.jsoup/jsoup "1.10.3"]
                 [agynamix/permissions "0.2.2-SNAPSHOT"]
                 [binaryage/devtools "0.9.7"]
                 [markdown-clj "1.0.1"]]

  :plugins [
            [lein-cljsbuild "1.1.7"]
            [lein-environ "1.1.0"]
            [lein-asset-minifier "0.4.3"]
            [lein-sass "0.4.0"]
            [lein-ancient "0.6.14"]
            [lein-codox "0.10.2"]
            [lein-kibit "0.1.5"]]

  :min-lein-version "2.5.3"

  :jvm-opts ["-Xmx3g" "-Xverify:none"]
  ;;:bootclasspath true
  ;:eval-in :classloader => doesn't work with the minifyer, sass, etc.

  :uberjar-name "numerix.jar"

  :main numerix.main

  :clean-targets ^{:protect false} [:target-path
                                    :compile-path
                                    "resources/public/js"
                                    "resources/public/css"]

  :codox {:language :clojurescript
          :source-paths ["src/cljs" "src/cljc"]}

  :sass {:src "src/scss"
         :matches ["style.scss" "landing.scss" "printable-styles.scss"]
         :output-directory "resources/public/css"
         :output-extension "css"
         :source-maps true
         :command :sassc
         :style :compressed}

  :minify-assets [[:js
                   {:target "resources/public/js/landing.min.js"
                    :source ["resources/vendor-js/jquery/jquery-3.1.1.min.js"
                             "resources/vendor-js/bootstrap/bootstrap.js"
                             "resources/vendor-js/bootstrap/jqBootstrapValidation.js"
                             "resources/vendor-js/landing/cbpAnimatedHeader.js"
                             "resources/vendor-js/landing/jquery.easing.min.js"
                             "resources/vendor-js/landing/jquery.fittext.js"
                             "resources/vendor-js/landing/jquery.waypoints.min.js"
                             "resources/vendor-js/landing/affix.js"
                             "resources/vendor-js/landing/contact_me.js"
                             "resources/vendor-js/landing/creative.js"
                             "resources/vendor-js/landing/cookieconsent.min.js"
                             "resources/vendor-js/snackbar/snackbar.js"
                             ]
                    :opts {;:linebreak 80
                           :optimization :simple}
                    }]
                  [:js
                   {:target "resources/public/js/vendor.min.js"
                    :source ["resources/vendor-js/polyfills/file_api.js"
                             "resources/vendor-js/jquery/jquery.caret.js"
                             "resources/vendor-js/jquery/jquery.atwho.js"
                             "resources/vendor-js/fullcalendar-3.4.0/fullcalendar.js"
                             ]
                    :opts {;:linebreak 80
                           :optimization :simple}
                    }]]


  :doo {:paths {:karma "karma"}}

  :repl-options {
                 :init-ns user
                 ;; If nREPL takes too long to load it may timeout,
                 ;; increase this to wait longer before timing out.
                 ;; Defaults to 30000 (30 seconds)
                 :timeout 120000
                 }

  :cljsbuild {:builds {:app {:source-paths ["src/cljs" "src/cljc"]
                             :figwheel { :on-jsload "numerix.app/render-root"}

                             :compiler {:main numerix.app
                                        ;:preloads [devtools.preload]
                                        ;:preamble      ["resources/vendor-js/material-ui/material.js"]
                                        :output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :externs         ["resources/externs/quill.js"
                                                          "resources/externs/fullcalendar.js"]
                                        :foreign-libs [
                                                       {:file "resources/vendor-js/quill/quill.js"
                                                        :file-min "resources/vendor-js/quill/quill.min.js"
                                                        :provides  ["cljsjs.quill"]}
                                                       ;{:file "resources/vendor-js/fullcalendar-3.4.0/fullcalendar.js"
                                                       ; :file-min "resources/vendor-js/fullcalendar-3.4.0/fullcalendar.min.js"
                                                       ; :provides  ["fullcalendar.core"]}
                                                       ]
                                        ;:npm-deps {:highlight "9.12.0"}
                                        ;:install-deps true
                                        :asset-path   "/js/out"
                                        :optimizations :none
                                        :source-map-timestamp true
                                        :parallel-build false
                                        :pretty-print  true}}}}

  :figwheel {
             :http-server-root "public"
             :server-port 3449
             :css-dirs ["resources/public/css"]
             :open-file-command "open-in-cursive.sh"}


  :profiles {:dev {:dependencies [[ring-mock "0.1.5"]
                                  [ring/ring-devel "1.6.2"]
                                  [leiningen "2.7.1"]
                                  [figwheel "0.5.14"]
                                  [figwheel-sidecar "0.5.14"]
                                  [asset-minifier "0.2.4"]
                                  [com.cemerick/piggieback "0.2.2"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [pjstadig/humane-test-output "0.8.3"]
                                  [doo "0.1.8"]]

                   :plugins [[lein-figwheel "0.5.13"]
                             [lein-doo "0.1.7"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev? "true"}

                   :cljsbuild {:builds {:test {:source-paths ["src/cljs" "src/cljc" "test/cljs"]
                                               :compiler {
                                                          ;:output-to "resources/test/compiled.js"
                                                          :output-to "target/test.js"
                                                          :main "numerix.runner"
                                                          :externs         ["resources/externs/quill.js"]
                                                          :foreign-libs [{:file "resources/vendor-js/quill/quill.min.js"
                                                                          :provides  ["cljsjs.quill"]}]
                                                          ;:externs         ["resources/externs/react-image-crop.js"]
                                                          ;:foreign-libs [{:file "resources/vendor-js/react-image-crop/bundle.js"
                                                          ;                :provides  ["react-image-crop"]}]
                                                          :optimizations :none
                                                          :parallel-build false}}}
                               ;:pretty-print true
                               ;:preamble ["react/react.js"]



                               :test-commands {"unit" ["phantomjs"
                                                       "resources/test/unit-test.js"
                                                       "resources/test/unit-test.html"]}}}

             :uberjar {:source-paths ^:replace ["src/clj" "src/cljc"]
                       :hooks [leiningen.cljsbuild leiningen.sass minify-assets.plugin/hooks]
                       :env {:production "true"}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                            {:source-paths ^:replace ["src/cljs" "src/cljc"]
                                             :compiler {
                                                        :preloads nil
                                                        :optimizations :advanced
                                                        :pretty-print false}}}}}})
