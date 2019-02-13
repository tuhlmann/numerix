(ns numerix.runner
  (:require [cljs.test :refer-macros [run-all-tests]]
            [numerix.app-test]))

; [doo.runner :refer-macros [doo-tests]]
;(doo-tests 'numerix.app-test)

(enable-console-print!)

(defn ^:export run []
  (run-all-tests #"numerix.*-test"))