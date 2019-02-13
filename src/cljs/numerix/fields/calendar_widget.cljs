(ns numerix.fields.calendar-widget
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$! $]]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [numerix.fields.markdown-render :as markdown]
            [numerix.fields.autoscroll :as scroll]
            [numerix.site :as site]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]
            [numerix.api.project :as prj-api]
            [re-frame.core :as rf]
            [re-com.core :as re-com]
            [cljs-time.coerce :as tc]
            [goog.date.relative :as rel-date]
            [numerix.lib.datatypes :as d]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [numerix.api.calendar :as calendar-api]
            [numerix.api.crud-ops :as crud-ops]
            [numerix.views.fields :as f]))

(defn on-key-down [cancel-fn event]
  (log/info "cancel-fn " event)
  (cond

    (= (.-keyCode event) (:ESC d/keycodes))
    (cancel-fn)

    )
  ;(reset! return-enabled true)
  )

(defn dialog-view-markup [cal-item process-cancel]
  (log/info "cal-item " (pr-str (js->clj @cal-item)) " , title " (js->clj (:title @cal-item)))
  (h/tty-log @cal-item)
  [:div.card
   [:div.card-header "Calendar Event"]
   [:img.card-img-top]
   [:div.card-body
    [:h4.card-title "View Event"]
    [:p.card-text "This is a longer card with supporting text below as a natural lead-in to "
     "additional content. This content is a little bit longer."]
    [:p.card-text (:summary @cal-item)]
    [:p.card-text [:small.text-muted "last updated " (h/format-date "dd.MM.yyyy HH:mm" (tc/from-long (:created @cal-item)))]]

    [f/static-field :label "Title"    :value (:title @cal-item)]
    [f/static-field :label "Start"    :value (h/format-date "dd.MM.yyyy HH:mm" (tc/from-long (:start @cal-item)))]
    [f/static-field :label "End"      :value (h/format-date "dd.MM.yyyy HH:mm" (tc/from-long (:end @cal-item)))]

    ]

   [:div.card-footer
    [:div.pull-right
     [:div.btn-group
      [:button.btn.btn-outline-secondary.btn-sm
       {:type :button
        :on-click process-cancel}
       "Close"]]
     ]]])

(defn dialog-edit-markup [cal-item process-ok process-cancel]
  [:div.card
   [:div.card-header "Calendar Event"]
   [:img.card-img-top]
   [:div.card-body
    [:h4.card-title "Edit Event"]
    [:p.card-text "This is a longer card with supporting text below as a natural lead-in to "
     "additional content. This content is a little bit longer."]

    [:div.form-group.row
     [:label.col-12.col-sm-2.form-control-label "Title"]
     [:div.col-12.col-sm.2
      [:input.form-control {:type  "text"
                            :value (:title @cal-item)
                            :on-change #(swap! cal-item assoc :title (-> % .-target .-value))}]]]

    [:div.form-group.row
     [:label.col-12.col-sm-2.form-control-label "Start"]
     [:div.col-6.col-sm-3
      [re-com/datepicker-dropdown
       :model (:start @cal-item)
       :format "dd.MM.yyyy"
       :show-today? true
       :on-change (fn [d]
                    (log/info "change date " (pr-str d))
                    ;(dispatch [:form-data-field-change :date d])
                    )
       ]]
     ;[:div.col-6.col-sm-3
     ; [re-com/input-time
     ;  :model        an-int-time
     ;  :minimum      @minimum
     ;  :maximum      @maximum
     ;  :on-change    #(reset! an-int-time %)
     ;  :disabled?    false
     ;  :hide-border? false
     ;  :show-icon?   true]
     ;
     ; ]
     ]

    [:div.form-group.row
     [:label.col-12.col-sm-2.form-control-label "End"]
     [:div.col-6.col-sm-3
      [re-com/datepicker-dropdown
       :model (:end @cal-item)
       :format "dd.MM.yyyy"
       :show-today? true
       :on-change (fn [d]
                    (log/info "change date " (pr-str d))
                    ;(dispatch [:form-data-field-change :date d])
                    )
       ]]]

    ]

   [:div.card-footer
    [:div.pull-right
     [:div.btn-group.mr-2
      [:button.btn.btn-outline-secondary.btn-sm
       {:type :button
        :on-click process-cancel}
       "Cancel"]]
     [:div.btn-group
      [:button.btn.btn-outline-primary.btn-sm
       {:type :button
        :on-click process-ok}
       "Save"]]
     ]]])

(defn event-dialog-modal
  "Create or edit event"
  [calendar-elem]
  (let [show-modal-mode (rf/subscribe [:form-state-field :calendar-modal-show-mode])
        form-data (rf/subscribe [:form-data])
        process-ok (fn [event]
                     (rf/dispatch [:crud-ops/save
                                   {:type :cal-item
                                    :record @form-data
                                    :next-fn
                                    (fn [new-record on-success]
                                      (let [momentized
                                             (-> new-record
                                                             (assoc :start (tc/to-string (:start new-record))
                                                                    :end (tc/to-string (:end new-record))))

                                             _ (h/tty-log "momentized " (clj->js momentized))

                                             ]

                                        (.fullCalendar @calendar-elem "renderEvent" (clj->js momentized) false) ;stick? = true

                                         )
                                       (on-success new-record))

                                     }])

                     (rf/dispatch [:hide-calendar-modal]))
        process-cancel (fn [event]
                         (rf/dispatch [:hide-calendar-modal-clear-form-data]))]

    (fn []
      (when-not (= @show-modal-mode :none)
        (if (= @show-modal-mode :view)
          [re-com/modal-panel
           :class "calendar-item-modal"
           :backdrop-color    "grey"
           :backdrop-opacity  0.4
           :wrap-nicely?      true
           :backdrop-on-click process-cancel
           :attr {
                  :on-key-press (partial on-key-down process-cancel)
                  }
           :child [dialog-view-markup
                   form-data
                   process-cancel]]

          [re-com/modal-panel
           :class "calendar-item-modal"
           :backdrop-color    "grey"
           :backdrop-opacity  0.4
           :wrap-nicely?      true
           :backdrop-on-click process-cancel
           :attr {
                  :on-key-press (partial on-key-down process-cancel)
                  }
           :child [dialog-edit-markup
                   form-data
                   process-ok
                   process-cancel]]

          )))))


