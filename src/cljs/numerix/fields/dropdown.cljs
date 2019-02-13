(ns numerix.fields.dropdown
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$]]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [numerix.site :as site]
            [numerix.state :as s]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]
            [re-frame.core :as rf]))


(defn dropdown-field
  "A Reagent dropdown field using Bootstrap styling and no jQuery
  Expects the following keys:

  - :close-on-outside-clk if true will close the dropdown if clicked somewhere outside,
    if false the dropdown will only be closed by clicking on the button or on one of the actions.

  - :orientation either :left or :right Adds pull-right if the dropdown should be right aligned
  "
  [& {:keys [dropdown-toggle-fn dropdown-content-fn close-on-outside-clk orientation]
      :or {
           close-on-outside-clk true
           orientation :left
           }}]
  (r/with-let
    [id (h/short-rand)
     watch-key (keyword "dropdown-wrapper-clk-" id)
     open (r/atom false)
     mouse-inside (r/atom false)
     wrapper-click (rf/subscribe [:form-state-field :wrapper-click])

     _ (when close-on-outside-clk
         (add-watch
           wrapper-click
           watch-key
           (fn [_ _ old new]
             (when (and (not @mouse-inside) (not= old new))
               (reset! open false)))))]

    @wrapper-click
    [:div.dropdown {:class (if @open "show" "")
                    :on-mouse-enter (handler-fn (reset! mouse-inside true))
                    :on-mouse-leave (handler-fn (reset! mouse-inside false))}
     (dropdown-toggle-fn {:on-click (handler-fn (reset! open (not @open)))})

     (dropdown-content-fn {:class (str
                                    (if (= orientation :right) "dropdown-menu-right " "dropdown-menu-left ")
                                    (if @open "show" ""))
                           :on-click (handler-fn (reset! open false))})
     ]

    (finally
      (remove-watch wrapper-click watch-key))))


(defn dropdown-btn
  "A Reagent dropdown button using Bootstrap styling and no jQuery
  Expects the following keys:
  - :button markup for the title of the button
  - :actions a list of maps that define the actions of the dropdown:
    - :title markup for the title of the action
    - :handler a handler function called when :on-click

  - :close-on-outside-clk if true will close the dropdown if clicked somewhere outside,
    if false the dropdown will only be closed by clicking on the button or on one of the actions.

  - :orientation either :left or :right Adds pull-right if the dropdown should be right aligned
  "
  [& {:keys [button actions close-on-outside-clk orientation]
      :or {
           close-on-outside-clk true
           orientation :left
           }}]
  (let [keyed-actions (map #(assoc % :key (enc/uuid-str 5)) actions)]

    (fn []
      [dropdown-field
       :orientation orientation
       :close-on-outside-clk close-on-outside-clk
       :dropdown-toggle-fn
       (fn [props]
         [:button.btn.btn-default.btn-sm.dropdown-toggle
          (r/merge-props {:type "button"} props)
          button])

       :dropdown-content-fn
       (fn [props]

         [:ul.dropdown-menu (r/merge-props {} props)
          (for [action keyed-actions]
            ^{:key (:key action)}
            [:li.dropdown-item
             [:a {:href (:href action "Javascript://")
                  :on-click (:handler action)
                  } (:title action)]])])])))
