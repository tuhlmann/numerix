(ns numerix.fields.selecter
  (:require [reagent.core :as r]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]))

;; A wrapper around react-select

;(defn text-field2 []
;  (let [this (r/current-component)
;        value (r/atom "Hi")]
;    (r/create-element (aget js/MaterialUI "TextField")
;                  #js{:className "foo" :value (get-in @app-state [:name :first])
;                      :onChange (fn [el]
;                                  (println "on change")
;                                  ;(swap! value (fn [_] (str "Puhh")))
;                                  (js/console.log (-> el .-target .-value))
;                                  (swap! app-state assoc-in [:name :first] (-> el .-target .-value))
;                                  (println "on change2 " @value)
;                                  (r/flush)
;                                  )}
;                  )))


(defn selecter []
  (let [opts {
              :allowCreate true
              :disabled true
              }]

    ;[selecter (clj->js {"options" opts})]

    (r/create-element js/Select (clj->js opts))

      ))