(ns numerix.views.calendar
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [re-frame.db :as db]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.fields.chat-room :as chat-room]
            [numerix.views.base :as base]
            [numerix.fields.quill :as quill]
            [numerix.events.common]
            [numerix.events.calendar]
            [numerix.lib.helpers :as h]
            [numerix.site :as site]
            [clojure.string :as str]
            [numerix.lib.datatypes :as d]
            [clojure.data]
            [cljs-time.core :as t :refer [now days minus day-of-week]]
            [cljs-time.format :refer [formatter formatters parse unparse]]
            [clojure.string :as str]
            [re-com.core :refer [row-button single-dropdown v-box h-box md-circle-icon-button
                                 datepicker datepicker-dropdown]
             :refer-macros [handler-fn]]
            [numerix.api.cache :as cache]
            [validateur.validation :as v]
            [numerix.history :as history]
            [numerix.fields.tag-input :as tag]
            [cuerdas.core :as cue]
            [numerix.api.crud-ops :as crud-ops]
            [numerix.api.tag :as tag-api]
            [numerix.fields.selecter :as selecter]
            [numerix.fields.calendar-widget :as calendar-widget]
            [numerix.api.calendar :as calendar-api]
            [re-frame.core :as rf]
            [cljs-time.coerce :as tc]
            [reagent.session :as session]))

;; FIXME: Both transformers will need to transform the moment date into a cljs date

(defn fc->clj [cal-event]
  (-> cal-event
      (js->clj :keywordize-keys true)
      (calendar-api/moment->dates)))

(defn clj->fc [cal-event]
  (clj->js cal-event))

;; TODO: Check if selected-item-id was loaded, then get the event from fullCalendar and dispatch a msg to modal it.
(defn fetch-events-fn [start end timezone callback-fn]
  (calendar-api/fetch-cal-items-for-range
    start
    end
    callback-fn))

(defn calendar-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Title"    :value (:title @form-data)]
       [f/static-field :label "Summary"  :value (:text @form-data)]
       [f/static-field :label "Start"    :value (h/format-date "dd.MM.yyyy HH:mm" (tc/from-long (:start @form-data)))]
       [f/static-field :label "End"      :value (h/format-date "dd.MM.yyyy HH:mm" (tc/from-long (:end @form-data)))]
       ])))

(defn calendar-detail-edit-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (h/tty-log "form-data: " @form-data)
    (fn []
      [:form
       [f/pfield :label "Title" :path :title]
       [f/pfield :label "Summary" :path :text :type :textarea :rows 5]
       [f/pfield :label "Start" :path :start :type :date]
       [f/pfield :label "End" :path :end :type :date]
       ])))


(defn calendar-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        master-config (rf/subscribe [:master-config])
        calendar-elem (js/$ (str "#" (:widget-id @master-config)))
        user (rf/subscribe [:current-user])
        calitem-data (:selected-item @form-state {})
        form-config {:mode          :edit
                     :title         [:span "Add Calendar Item"]
                     :title-subtext [:span "Add a new Calendar Item"]
                     :card-title    [:span (:title calitem-data)]
                     :inner-form    calendar-detail-edit-inner
                     :form-data     calitem-data
                     :validation    (v/validation-set
                                      (v/length-of :title :within (range 3 200) :blank-message "Please provide a title"))

                     :buttons {
                               :cancel {
                                        :tt      "Reset Calendar Item"
                                        :handler (fn [_]
                                                   (rf/dispatch [:common/show-details-view-item]))
                                        }
                               :save   {
                                        :tt      "Save Calendar Item"
                                        :handler (fn [form-data]
                                                   (rf/dispatch [:crud-ops/save
                                                                 {:type :cal-item
                                                                  :record (calendar-api/strip-client-fields form-data)
                                                                  :next-fn
                                                                  (fn [new-record on-success]
                                                                    (let [momentized (calendar-api/dates->moment new-record)
                                                                          _ (h/tty-log "momentized " (clj->js momentized))
                                                                          ]

                                                                      (.fullCalendar calendar-elem "renderEvent" (clj->js momentized) false) ;stick? = true
                                                                      )
                                                                    (on-success new-record))

                                                                  }]))
                                        }
                               :project-switch {:tt "Switch Project"
                                                :handler (fn [form-data project]
                                                           (rf/dispatch [:crud-ops/switch-project
                                                                      {:type :cal-item
                                                                       :record form-data
                                                                       :project project}]))
                                                }
                               }
                     }]

    ;(s/swap-form-config! form-config)
    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-modal])))

(defn calendar-detail-view [item]
  (let [master-config (rf/subscribe [:master-config])
        calendar-elem (js/$ (str "#" (:widget-id @master-config)))
        form-config {:mode :view
                     :title         [:span "Calendar Item" ]
                     :title-subtext [:span "View Calendar Item"]
                     :card-title    [:span "Calendar Item "]
                     :inner-form    calendar-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :project-view {:tt "Current Project"}

                                     :edit {
                                            :tt "Edit Calendar Item"
                                            :handler (fn [_]
                                                       (rf/dispatch [:common/show-details-edit-item true]))
                                            }
                                     :remove {
                                              :tt "Remove Calendar Item"
                                              :handler (fn [form-data]
                                                         (rf/dispatch [:crud-ops/remove
                                                                       {:type :cal-item
                                                                        :record form-data
                                                                        :next-fn
                                                                        (fn [removed-rec on-success]
                                                                          (.fullCalendar
                                                                            calendar-elem
                                                                            "removeEvents"
                                                                            (fn [src]
                                                                              (let [csrc (js->clj src :keywordize-keys true)]
                                                                                (= (:_id csrc) (:_id removed-rec)))))
                                                                          (when-let [link-fn (:master-list-link @master-config)]
                                                                            (history/navigate! (link-fn)))

                                                                          (on-success removed-rec))
                                                                        }]))
                                              }
                                     }
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-modal])))


(defn calendar-detail-area [calendar-elem]
  (let [form-state (rf/subscribe [:form-state])]

    (fn []
      ;(log/info "anything to show? " (pr-str @form-state))
      (cond
        (:edit-item @form-state)
        (do
          ;(log/info "detail edit " (pr-str @form-state))
          [calendar-detail-edit])

        (:show-details @form-state)
        (do
          ;(log/info "detail view: " (pr-str @form-state))
          (when-let [list-item (:selected-item @form-state)]
            [calendar-detail-view list-item]))

        ))))

(defn calendar-main-page []
  (let [user (rf/subscribe [:current-user])
        master-config (rf/subscribe [:master-config])
        memberships (rf/subscribe [:memberships-sort-by-name])
        events (rf/subscribe [:master-data])
        form-data-create? (rf/subscribe [:form-data-allowed-create-new])
        selected-item-start-date (rf/subscribe [:form-state-selected-item-start-date]) ;; FIXME: React on it!
        calendar-elem (r/atom nil)]

    (r/create-class
      {:display-name
       "calendar-widget"

       :component-did-mount
       (fn [this]
         (let [_ (reset! calendar-elem (js/$ (str "#" (:widget-id @master-config))))
               select-fn (fn [start end]
                           (when-let [allow-create-records (and (:allow-create-records master-config true) @form-data-create?)]
                             (let [new-event (crud-ops/new-record @user
                                                                  {
                                                                   :title "New Event"
                                                                   :start (tc/from-long start)
                                                                   :end   (tc/from-long end)
                                                                   :allDay true
                                                                   })]
                               (rf/dispatch [:common/show-details-edit-item-and-set true new-event])
                               (.fullCalendar @calendar-elem "unselect"))))

               event-click-fn (fn [cal-event]
                                ;(h/tty-log "current cal event " cal-event)
                                (rf/dispatch [:select-item-and-show-details (fc->clj cal-event)]))

               dayClickFn (fn [date all-day]
                            (log/info "day click cal event " (pr-str date) (pr-str all-day)))

               change-event-fn (fn [cal-event delta]
                                 (h/tty-log "resize event " cal-event delta))

               options {
                        :header       {
                                       :left   "prev,next today"
                                       :center "title"
                                       :right  "listMonth,month,listWeek,agendaWeek,agendaDay"
                                       }
                        :views        {
                                       :listMonth {"buttonText" "month agenda"}
                                       :listWeek  {"buttonText" "week agenda"}
                                       }
                        :navLinks     true ;can click day/week names to navigate views
                        :editable     true
                        :eventLimit   true ;allow "more" link when too many events
                        :selectHelper true
                        :selectable   true
                        :select       select-fn
                        :eventClick   event-click-fn
                        :events       fetch-events-fn
                        :eventResize  change-event-fn
                        :eventDrop    change-event-fn
                        ;:defaultDate  (js/moment "2017-07-01")
                        ;:dayClick dayClickFn
                        }
               ]
           (.fullCalendar @calendar-elem
                          (clj->js options))

           ))

       :reagent-render
       (fn []
         [base/base
          [:div {:key (enc/uuid-str 5)}
           [:div.row
            [:div.col-12

             [:div.card
              ;[:div.card-header "header"]
              [:div.card-body
               [:div {:id (:widget-id @master-config)}]
               [calendar-detail-area calendar-elem]
               ;[calendar-widget/event-dialog-modal calendar-elem]
               ]
              ]]]]]
         )

       })))

;;; Main entry for company master-detail page
(defn calendar-page []
  (let [page-config
        {
         :form-state    {
                         :form-name    :calendar
                         :edit-item    false
                         :show-details false
                         :is-loading   false
                         :mouse-inside-detail-area-no-follow true
                         :selected-item-id (session/get :selected-item-id)
                         }

         :master-config {
                         :widget-id "calendar-widget"
                         :master-list-item-std-detail-link #(site/cal-item-route {:cal-item-id (.toString (:_id %))})
                         :master-list-link #(site/calendar-route)
                         }
         }]

    (rf/dispatch-sync [:init-calendar-page page-config])
    (fn []
      [calendar-main-page])))

(rf/reg-event-fx
  :init-calendar-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    (let [new-db (-> db
                     (s/assoc-master-config master-config)
                     ;(s/assoc-form-state (merge (s/form-state db) form-state))
                     (s/assoc-form-state form-state)
                     )
          re {:db new-db}
          ]

      (if (s/get-in-form-state new-db :selected-item-id)
        (do
          (log/info "selected item id is " (s/get-in-form-state new-db :selected-item-id))
          (merge re {
                     :load-selected-cal-item-start-date
                     [(s/get-in-form-state new-db :selected-item-id)
                      (fn [start-date]
                        (.fullCalendar (js/$ (str "#" (:widget-id master-config))) "gotoDate" (tc/to-string start-date)))
                      ]}))
        re))))




