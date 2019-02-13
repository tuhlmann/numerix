(ns numerix.log-config
  (:require [taoensso.timbre                :as log]
            [numerix.config                 :as cfg]
            [taoensso.timbre.appenders.core :as core-appenders]
            [taoensso.timbre.appenders.3rd-party.rolling :as rolling-appender]))

(defn appenders-for-run-mode
  "calculates the appenders to user for the current run mode"
  []
  ;(println "Run in production mode? " (cfg/production-mode?))
  (if (cfg/log-path)
    (if (cfg/production-mode?)
      {
       :rolling (rolling-appender/rolling-appender
                  {:path (cfg/log-file)
                   :pattern :daily
                   :min-level  :info
                   })
       }

      {
       :rolling (rolling-appender/rolling-appender
                  {:path (cfg/log-file)
                   :pattern :daily
                   :min-level  :debug
                   })
       })

    (do
      (println "No logfile found, using println appender")
      {
       :println (core-appenders/println-appender {:stream :auto})
       })))

(defn numerix-log-config []
  "Example (+default) Timbre v4 config map.

  APPENDERS
    An appender is a map with keys:
      :min-level       ; Level keyword, or nil (=> no minimum level)
      :enabled?        ;
      :async?          ; Dispatch using agent? Useful for slow appenders
      :rate-limit      ; [[ncalls-limit window-ms] <...>], or nil
      :output-fn       ; Optional override for inherited (fn [data]) -> string
      :fn              ; (fn [data]) -> side effects, with keys described below

    An appender's fn takes a single data map with keys:
      :config          ; Entire config map (this map, etc.)
      :appender-id     ; Id of appender currently dispatching
      :appender        ; Entire map of appender currently dispatching

      :instant         ; Platform date (java.util.Date or js/Date)
      :level           ; Keyword
      :error-level?    ; Is level e/o #{:error :fatal}?
      :?ns-str         ; String, or nil
      :?file           ; String, or nil
      :?line           ; Integer, or nil ; Waiting on CLJ-865

      :?err_           ; Delay - first-arg platform error, or nil
      :vargs_          ; Delay - raw args vector
      :hostname_       ; Delay - string (clj only)
      :msg_            ; Delay - args string
      :timestamp_      ; Delay - string
      :output-fn       ; (fn [data]) -> formatted output string
                       ; (see `default-output-fn` for details)

      :context         ; *context* value at log time (see `with-context`)
      :profile-stats   ; From `profile` macro

  MIDDLEWARE
    Middleware are simple (fn [data]) -> ?data fns (applied left->right) that
    transform the data map dispatched to appender fns. If any middleware returns
    nil, NO dispatching will occur (i.e. the event will be filtered).

  The `example-config` source code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  {:level :info  ; e/o #{:trace :debug :info :warn :error :fatal :report}

   ;; Control log filtering by namespaces/patterns. Useful for turning off
   ;; logging in noisy libraries, etc.:
   :ns-whitelist  [] #_["my-app.foo-ns"]
   :ns-blacklist  [] #_["taoensso.*"]

   :middleware [] ; (fns [data]) -> ?data, applied left->right

   :timestamp-opts log/default-timestamp-opts ; {:pattern _ :locale _ :timezone _}

   :output-fn log/default-output-fn ; (fn [data]) -> string

   :appenders (appenders-for-run-mode)

  })

