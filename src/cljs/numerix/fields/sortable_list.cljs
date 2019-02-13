(ns numerix.fields.sortable-list
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]))

(defn sortable-list [& {:keys [on-drop]}]
  (let [container-id (h/short-rand)
        drag-start-idx (atom 0)
        drag-stop-idx (atom 0)
        sort-opts {:axis "y"
                   :revert true
                   :cursor "move"
                   :delay 150
                   :opacity 0.8
                   :start (fn [e ui]
                            (reset! drag-start-idx (.. ui -item index)))
                   :stop (fn [e ui]
                           (reset! drag-stop-idx (.. ui -item index))
                           (when (not= @drag-start-idx @drag-stop-idx)
                             (js/$
                               (fn []
                                 (.sortable (js/$ (str "#" container-id)) "cancel" )))
                             (when (fn? on-drop)
                               (on-drop @drag-start-idx @drag-stop-idx))))
                   }]
    (r/create-class
      {
       :component-did-mount
       (fn [this]
         (js/$
           (fn []
             (.sortable (js/$ (str "#" container-id)) (clj->js sort-opts) ))))

       ;:component-will-update
       ;(fn [this]
       ;  (log/info "we update")
       ;  (js/$
       ;    (fn []
       ;      (.sortable (js/$ (str "#" container-id)) "refresh" )))
       ;  )

       :reagent-render
       (fn [& {:keys [children trigger]}]
         ;(log/info "render " (map #(.toString %) @trigger))
         [:ul.list-unstyled {:id container-id}
          (map-indexed (fn [idx child]
                         ^{:key idx}
                         [:li child]
                         ) children)])
       })))