(ns numerix.fields.flyout
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [numerix.fields.markdown-render :as markdown]
            [numerix.site :as site]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]
            [numerix.api.project :as prj-api]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [numerix.lib.datatypes :as d]))

"
  Implements a flyout widget shown on top of a page.
"

(defn on-key-down [close-flyout-fn event]
  (log/info "on-key-down " event)
  (cond

    (= (.-keyCode event) (:ESC d/keycodes))
    (close-flyout-fn)

    )
  ;(reset! return-enabled true)
  )

(defn flyout [params children]
  (r/with-let
    [{:keys [class close-on-outside-clk close-flyout-fn]
      :or {class ""
           close-on-outside-clk true}} params
     mouse-inside (r/atom false)
     wrapper-click (rf/subscribe [:form-state-field :wrapper-click])

     _ (when close-on-outside-clk
         (add-watch
           wrapper-click
           :flyout-wrapper-clk
           (fn [_ _ old new]
             (when (and (not @mouse-inside) (not= old new) (fn? close-flyout-fn))
               (close-flyout-fn)
               ))))]

      (let []
        [:div.flyout-wrapper
         {:class class
          :tab-index "1"
          :on-mouse-enter (handler-fn (reset! mouse-inside true))
          :on-mouse-leave (handler-fn (reset! mouse-inside false))
          :on-key-down (partial on-key-down close-flyout-fn)
          }

         [:header.flyout-header-container
          [:div.flyout-header.clearfix
           [:div.flyout-header-custom-items
             (:header-items params)]
           [:span.pull-right.clickable.flyout-close-btn
            {:on-click #(close-flyout-fn)}
            [:i.fa.fa-close]]]]

         [:div.flyout-container children]])

    (finally
      (remove-watch wrapper-click :flyout-wrapper-clk))
    ))


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
  (let [open (r/atom false)
        mouse-inside (r/atom false)
        wrapper-click (rf/subscribe [:form-state-field :wrapper-click])]

    (when close-on-outside-clk
      (add-watch wrapper-click nil (fn [_ _ old new]
                                     (when (and @open (not @mouse-inside) (not= old new))
                                       (reset! open false)))))
    (fn []
      @wrapper-click
      [:div.dropdown {:class (if @open "open" "")
                      :on-mouse-enter (handler-fn (reset! mouse-inside true))
                      :on-mouse-leave (handler-fn (reset! mouse-inside false))}
       (dropdown-toggle-fn {:on-click (handler-fn (reset! open (not @open)))})

       (dropdown-content-fn {:class (if (= orientation :right) "dropdown-menu-right" "dropdown-menu-left")
                             :on-click (handler-fn (reset! open false))})
       ])))
