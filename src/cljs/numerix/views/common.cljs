(ns numerix.views.common
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [re-com.core :refer-macros [handler-fn]]
            [secretary.core :refer [dispatch!]]
            [numerix.views.base :as base]
            [numerix.views.common-controls :as ctrl]
            [numerix.history :as history]
            [taoensso.encore :as enc]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [cuerdas.core :as cue]
            [numerix.state :as s]
            [numerix.views.fields :as f]
            [numerix.site :as site]
            [numerix.api.project :as prj-api]
            [validateur.validation :as v]
            [numerix.fields.project-selector :as prj-sel]
            [numerix.fields.dropdown :as dropdown]
            [re-frame.core :as rf]
            [numerix.lib.helpers :as h]
            [re-frame.db :as db]
            [re-com.core :as re-com]
            [cljs-time.coerce :as tc]))

;;; Helpers

(def css-transition-group
  (r/adapt-react-class js/React.addons.CSSTransitionGroup))


(defn active-if-selected [item selected-item]
  (if (= item selected-item)
    "active"
    ""))

(defn find-selected-item
  "Receives a list of items, checks for the query-param `selected`.
  If defined, finds the item with that ID, defaults to the first item."
  [item-list]
  ;(log/info "selected id " (session/get :selected-item-id))
  (enc/if-let [id (session/get :selected-item-id)
                elem (first (filter #(= (.toString (:_id %)) id) item-list))]

               elem
               nil
               #_(first item-list)))

(defn no-item-found [item-name]
  (let [master-config (rf/subscribe [:master-config])
        allow-create-records (and
                               (:allow-create-records @master-config true)
                               @(rf/subscribe [:form-data-allowed-edit]))]
    [:div.card.card-body
     [:h4.card-title "No Items Found"]
     [:p.card-text "No items have been found matching your query."
      [:br]
      (when allow-create-records
        "Click on the link below to add a new item.")]
     (when allow-create-records
       [:button.btn.btn-outline-primary {:on-click #(rf/dispatch [:show-details-add-item])}
        (str "Create new " item-name)])]))

(defn master-area-control-btn [search-query]
  (if (seq search-query)
    [:a {:href "Javascript://"
         :on-click #(rf/dispatch [:master-list-filter-change ""])}
     [:i.fa.fa-times]]
    [:i.fa.fa-search]))

(defn master-area-control
  "Renders a control panel above the master list"
  [master-config]
  (let [form-state (rf/subscribe [:form-state])
        search-query (rf/subscribe [:master-list-filter])
        form-data-create? (rf/subscribe [:form-data-allowed-create-new])]
    (fn []
      (let [allow-create-records (and (:allow-create-records master-config true) @form-data-create?)]
        [:div.list-group-item
         [:div.search-form
          [:div.row
           [:div.p-l-0.p-r-0 {:class (if allow-create-records "col-8" "col-12")}
            [:div.form-group.m-b-0
             [:div.right-inner-addon
              [master-area-control-btn @search-query]
              [:input.form-control.has-feedback {
                                                 :type "text"
                                                 :placeholder "Search..."
                                                 :value (or @search-query "")
                                                 :on-change (fn [v]
                                                              (rf/dispatch [:master-list-filter-change (.-target.value v)]))}]]]]
           (when allow-create-records
             [:div.col-4.p-r-0.p-l-0
              [:a.pull-right.btn.btn-outline-primary {:on-click #(rf/dispatch [:show-details-add-item])}
               (:master-area-control-create-lbl master-config [:span [:i.fa.fa-plus] " Add"])]])]]]))))



(defn master-list-item-std
  "Renders a list of items in the master list.
  This is a specific instance that knows how to render"
  []
  (let [form-state (rf/subscribe [:form-state])
        master-config (rf/subscribe [:master-config])]
    (when-let [item-desc (:inner-master-list-item-desc @master-config)]
      (fn [list-item]
        [:div.list-group-item {:class (active-if-selected list-item (:selected-item @form-state))}
         [:div.media.clickable {:on-click (fn []
                                            (if (fn? (:on-click item-desc))
                                              ((:on-click item-desc) @form-state @master-config list-item)
                                              (rf/dispatch [:select-item-and-show-details list-item])))}
          [:span.d-flex.mr-3 {:href "#"}
           (when (:img-url item-desc)
             (if (fn? (:img-url item-desc))
               [:img {:src ((:img-url item-desc) list-item) :style {:max-width "32px" :max-height "32px"}}]
               [:img {:src (:img-url item-desc) :style {:max-width "32px" :max-height "32px"}}])
             )
           (when (:img-icon item-desc)
             (if (fn? (:img-icon item-desc))
               [:i {:class ((:img-icon item-desc) list-item)}]

               [:i {:class (:img-icon item-desc)}]))]

          [:div.media-body

           [:div.media-heading ((:title item-desc) list-item)]
           (if (fn? (:description item-desc))
               ((:description item-desc) list-item)
               (:description item-desc))]

          [:span.pull-right [:i.fa.fa-chevron-right]]]]))))


(defn sort-master-data [master-config items]
  (let [sort-items-fn (:sort-items-fn master-config
                       (fn [items] items))]
    (sort-items-fn items)))

;; walk the item structure and concat all primitive values into one string, and search that
;; maybe specter select walk can help
(defn filter-master-data [master-config master-list filter-value]
  (let [full-text-fn (:full-text-fn master-config
                       (fn [i] (str/lower-case (:name i ""))))]
    (if (> (count filter-value) 0)
      (do
        ;(println "filter " filter-value)
        (filterv #(enc/str-contains? (full-text-fn %) (str/lower-case filter-value)) master-list))
      master-list)))

(defn list-item-or-detail-area []
  (let [form-state (rf/subscribe [:form-state])
        master-config (rf/subscribe [:master-config])
        master-data (rf/subscribe [:master-data])]
    (fn [list-item]
      (let [show-details? (ctrl/show-detail-area? @form-state)
            sel-list-item (:selected-item @form-state)]

        (if (and show-details? (= list-item sel-list-item))
          (do
            [:div.list-group-item.detail-item
             [:div.media
              [:div.media-body
               [:div ;.col-lg-10.offset-lg-1.col-12
                [(:detail-area-main @master-config)]]]]])


          (do
            [(:inner-master-list-item @master-config) list-item]))))))


(defn master-area-list
  "Renders the master list"
  []
  (let [form-state (rf/subscribe [:form-state])
        master-config (rf/subscribe [:master-config])
        master-data (rf/subscribe [:master-data])]
    (fn []
      [:div
       (if (empty? @master-data)
        [no-item-found "Entry"]
        [:div.list-group
         ;; FIXME Add a selector-fn to master-config that will re true if the item should be shown
         (for [item (sort-master-data @master-config
                      (filter-master-data @master-config @master-data (:master-list-filter @form-state)))]
           ^{:key (.toString (:_id item "no_id_given"))}
           [list-item-or-detail-area item])])])))





(def jquery (js* "jQuery"))

(defn calc-master-area-height []
  (- (-> (jquery js/window)
         (.height))
     200))

;; TODO: Add event listener to that component for window resize:
#_(will-mount [_]
              (.addEventListener js/window
                                 "resize" (fn []
                                            (let [{:keys [width height]} (get-div-dimensions id)]
                                              (om/update! cursor :div {:width width :height height})))))
(defn master-area
  "Renders the left side master area"
  [master-config]
  [:div.master-area.fat-border
   [:div.list-group {:style {:margin-bottom "0.7rem"}}
    [master-area-control master-config]]
   [master-area-list]])

(defn calc-master-panel-cls [form-state-map]
  (if (:enable-full-width-display form-state-map)
    "col-12 master-panel-full-width"
    "col-lg-10 offset-lg-1 col-12"))


(defn master-detail-page
  "Renders a master / detail page with a list panel at the left
  and a detail area at the right"
  []
  (let [form-state (rf/subscribe [:form-state])
        master-config (rf/subscribe [:master-config])]
    (fn []
      [base/base
       [:div {:key (enc/uuid-str 5)}
        [:div.row
         [(fn []
            (when-not (:show-details @form-state) ;was: (:detail-only-view @form-state)
              [:div {:class (calc-master-panel-cls @form-state)
                     :on-mouse-over #(rf/dispatch [:mouse-inside-detail-area true])
                     :on-mouse-leave #(rf/dispatch [:mouse-inside-detail-area false])}
               [master-area @master-config]]))]
         [(fn []
            (when (:show-details @form-state) ;was: (:detail-only-view @form-state)
              [:div {:class (calc-master-panel-cls @form-state)
                     :on-mouse-over #(rf/dispatch [:mouse-inside-detail-area true])
                     :on-mouse-leave #(rf/dispatch [:mouse-inside-detail-area false])}
               [(:detail-area-main @master-config)]]))]]]])))



(defn generic-form-cancel-button [form-state button-cfg]
  (enc/when-let [btn-conf (:cancel button-cfg)
                 form-data (rf/subscribe [:form-data])]
    (fn [form-state form-config]
      [:button.btn.btn-outline-danger.btn-sm {:title (:tt btn-conf)
                                              :on-click (fn []
                                                          (rf/dispatch [:common/detail-only-view false])
                                                          ((:handler btn-conf) @form-data))}
                                       [:i.fa.fa-times] " Cancel"])))


(defn generic-form-save-button [form-state button-cfg]
  (enc/when-let [btn-conf (:save button-cfg)
                 form-data (rf/subscribe [:form-data])
                 validation-errors (rf/subscribe [:form-state-errors])]
    (fn []
      ;(log/info "errors " (pr-str (s/validation-errors)))
      [:button.btn.btn-success.btn-sm {:on-click (fn []
                                                   (rf/dispatch [:common/detail-only-view false])
                                                   ((:handler btn-conf) @form-data))
                                       :disabled (v/invalid? @validation-errors) ;(s/validation-errors))
                                       :title    (:tt btn-conf)}
                                      [:i.fa.fa-check] " Save"])))

(defn generic-form-invite-button [form-state button-cfg]
  (enc/when-let [btn-conf (:invite button-cfg)
                 form-data (rf/subscribe [:form-data])
                 validation-errors (rf/subscribe [:form-state-errors])]
    (fn []
      ;(log/info "errors " (pr-str (s/validation-errors)))
      [:button.btn.btn-success.btn-sm {:on-click (fn []
                                                   (rf/dispatch [:common/detail-only-view false])
                                                   ((:handler btn-conf) @form-data))
                                       :disabled (v/invalid? @validation-errors) ;(s/validation-errors))
                                       :title    (:tt btn-conf)}
                                      [:i.fa.fa-check] " Invite"])))


(defn cog-wheel-actions [form-state button-cfg]
  (when-let [btn-conf (:cog-wheel-actions button-cfg)]
    (fn []
      [:div.btn-group
       [dropdown/dropdown-btn
        :orientation :right
        :button [:i.fa.fa-cog]
        :actions (:actions btn-conf)]])))


(defn generic-form-send-button [form-state button-cfg]
  (enc/when-let [btn-conf (:send button-cfg)
                 form-data (rf/subscribe [:form-data])
                 validation-errors (rf/subscribe [:form-state-errors])]
    (fn []
      [:button.btn.btn-success.btn-sm {:on-click (fn [] ((:handler btn-conf) @form-data))
                                       ;:disabled (f/any-validation-failed (apply f/validate-all (vals validation)))
                                       :disabled (v/invalid? @validation-errors)
                                       :title    (:tt btn-conf)}
                                      [:i.fa.fa-check] " Send"])))


(defn generic-form-edit-button [form-state button-cfg]
  (enc/when-let [btn-conf (:edit button-cfg)
                 form-data (rf/subscribe [:form-data])
                 can-edit? @(rf/subscribe [:form-data-allowed-edit])]
    (fn [form-state button-cfg]
      [:button.btn.btn-primary.btn-sm {:title (:tt btn-conf)
                                       :on-click (fn [] ((:handler btn-conf) @form-data))}
                                      [:i.fa.fa-pencil] " Edit"])))


(defn generic-form-edit-tb-button [form-state button-cfg]
  (enc/when-let [btn-conf (:edit button-cfg)
                 form-data (rf/subscribe [:form-data])
                 can-edit? @(rf/subscribe [:form-data-allowed-edit])]
    (fn [form-state button-cfg]
      [:div.btn-group.spaced-btn-group.bordered
        [:a.text-muted {:href "Javascript://"
                        :title (:tt btn-conf)
                        :on-click (fn [] ((:handler btn-conf) @form-data))}
                       [:i.fa.fa-pencil]]])))


(defn generic-form-remove-button [form-state button-cfg]
  (enc/when-let [btn-conf (:remove button-cfg)
                 form-state (rf/subscribe [:form-state])
                 can-remove? @(rf/subscribe [:form-data-allowed-remove])]

                (fn []
                  [:button.btn.btn-danger.btn-sm {:title (:tt btn-conf)
                                                  :class (if (:form-data-pre-delete @form-state) "disabled" "")
                                                  :on-click (fn []
                                                              ;(log/info "toggle")
                                                              (rf/dispatch [:form-data-pre-delete true]))}

                   [:i.fa.fa-trash] " Remove"])))



(defn generic-form-preview-tb-button [form-state button-cfg]
  (enc/when-let [btn-conf (:preview button-cfg)
                 form-data (rf/subscribe [:form-data])]

                (fn [form-state button-cfg]
                  [:div.btn-group.spaced-btn-group.bordered
                   [:a.text-muted {:href "Javascript://"
                                   :title (:tt btn-conf "Preview")
                                   :on-click (fn [] ((:handler btn-conf) @form-data))}

                    [:i.fa.fa-eye]]])))


(defn generic-form-print-tb-button [form-state button-cfg]
  (enc/when-let [btn-conf (:print button-cfg)
                 form-data (rf/subscribe [:form-data])]

                (fn [form-state button-cfg]
                  [:div.btn-group.spaced-btn-group.bordered
                   [:a.text-muted {:href "Javascript://"
                                   :title (:tt btn-conf "Print")
                                   :on-click (fn [] ((:handler btn-conf) @form-data))}
                    [:i.fa.fa-print]]])))

(defn generic-form-activate-tb-button [form-state button-cfg]
  (enc/when-let [btn-conf (:activate button-cfg)
                 form-data (rf/subscribe [:form-data])]

                (fn [form-state button-cfg]
                  [:div.btn-group.spaced-btn-group.bordered
                   [:a.text-muted {:href "Javascript://"
                                   :title (:tt btn-conf "Activate")
                                   :on-click (fn [] ((:handler btn-conf) @form-data))}
                    [:i.fa.fa-check]]])))


(defn generic-form-project-view-tb-button [form-state button-cfg]
  (enc/when-let [btn-conf (:project-view button-cfg)
                 form-data (rf/subscribe [:form-data])]

                (fn [form-state form-config]
                  [:div.btn-group.spaced-btn-group.bordered
                   [:a.text-muted {:title (:tt btn-conf "Current Project")}
                    [:i.fa.fa-cubes] " " (:name (prj-api/find-project (:project-id @form-data))) ]])))

(defn generic-form-project-switch-tb-button [form-state button-cfg]
  (enc/when-let [btn-conf (:project-switch button-cfg)
                 form-data (rf/subscribe [:form-data])
                 form-id (:_id @form-data)
                 projects (rf/subscribe [:projects])
                 user (rf/subscribe [:current-user])]

                (fn [form-state button-cfg]
                  [:div.btn-group.spaced-btn-group.bordered
                   [prj-sel/item-project-switcher
                    @user
                    projects
                    (:project-id @form-data)
                    (partial (:handler btn-conf) @form-data)]
                   ;[:a.text-muted {:title (:tt btn-conf "Switch Project")}
                   ; [:i.fa.fa-cubes] "Switch: " (:name (prj-api/find-project (:project-id @form-data))) ]
                   ])))


(defn generic-form-toolbar-buttons
  "Creates a toolbar of buttons shown in the upper right of the form"
  [form-state button-cfg]

  [:div.btn-toolbar.pull-right
   [generic-form-project-view-tb-button form-state button-cfg]
   [generic-form-project-switch-tb-button form-state button-cfg]
   [generic-form-preview-tb-button form-state button-cfg]
   [generic-form-print-tb-button form-state button-cfg]
   [generic-form-activate-tb-button form-state button-cfg]
   [generic-form-edit-tb-button form-state button-cfg]
   [cog-wheel-actions form-state button-cfg]
   ])


(defn generic-form-buttons-left
  "The buttons part of the generic form.
  Can be configured through the button-cfg map"
  [form-state button-cfg]

  [:div.btn-group.pull-left
   [generic-form-remove-button form-state button-cfg]])

(defn generic-form-buttons-right
  "The buttons part of the generic form.
  Can be configured through the button-cfg map"
  [form-state button-cfg]

  [:div.btn-group.pull-right
   [generic-form-cancel-button form-state button-cfg]
   [generic-form-save-button form-state button-cfg]
   [generic-form-invite-button form-state button-cfg]
   [generic-form-send-button form-state button-cfg]
   [generic-form-edit-button form-state button-cfg]])

(defn generic-form-remove-confirm [form-state button-cfg]
  (let [pre-delete-state (rf/subscribe [:form-data-pre-delete])
        form-data (rf/subscribe [:form-data])
        btn-conf (:remove button-cfg)]
    (fn [form-state form-config]
      (when (boolean @pre-delete-state)
        [:div.row {:style {:margin-top "1em"}}
         [:div.col-12
          [:button.btn.btn-outline-danger.btn-sm.pull-left {
                                                            :type :button
                                                            :on-click #(rf/dispatch [:form-data-pre-delete false])}
                                               "Cancel"]
          [:button.btn.btn-danger.btn-sm.pull-right {
                                                     :type :button
                                                     :on-click (fn []
                                                                 (rf/dispatch [:common/detail-only-view false])
                                                                 ((:handler btn-conf) @form-data)
                                                                 (rf/dispatch [:form-data-pre-delete false])
                                                                 (rf/dispatch [:set-selected-item nil]))}
                                                    [:i.fa.fa-trash] " Remove"]]]))))


(defn show-area-leave? [form-config]
  (not
    (or (:standalone? form-config)
        (= (:mode form-config :view) :edit))))

(defn generic-form
  "A generic form component. This takes a configuration defining what to display
   and creates a predefined panel around it"
  []

  (let [form-state (rf/subscribe [:form-state])
        form-config (rf/subscribe [:form-config])
        form-data (rf/subscribe [:form-data])]

    (fn []
      (let [vali-res (f/validate-form-all @form-config @form-data)
            button-cfg (get-in @form-config [:buttons])
            button-cfg (if (fn? button-cfg)
                         (button-cfg @form-data)
                         button-cfg)]
        [:div.card.generic-form-detail-area
         [:div.card-header
          (when (show-area-leave? @form-config)
            [:span.pull-left.clickable.text-muted
             {:style {:margin-left "-15px"}
              :on-click #(rf/dispatch [:show-detail-area false])}
             [:i.fa.fa-chevron-left {:style {:margin-right "5px"}}]  " "])

          #_(when (show-area-leave? form-config)
             [:span.pull-right.clickable.text-muted
              {:style {:margin-left "-15px"}

               :on-click #(rf/dispatch [:show-detail-area false])}
              [:i.fa.fa-times]])
          [generic-form-toolbar-buttons @form-state button-cfg]
          (if (fn? (:title @form-config))
            ((:title @form-config) @form-data)
            (:title @form-config))
          [:br] [:small [:i
                         (if (fn? (:title-subtext @form-config))
                           ((:title-subtext @form-config) @form-data)
                           (:title-subtext @form-config))]]]
         [:div.card-body
          [:h4.card-title
           (if (fn? (:card-title @form-config))
             ((:card-title @form-config) @form-data)
             (:card-title @form-config))]

          [(:inner-form @form-config)]]

         [:div.card-footer

          [generic-form-buttons-left @form-state button-cfg]
          [generic-form-buttons-right @form-state button-cfg]

          [:div.clearfix]

          [generic-form-remove-confirm @form-state button-cfg]]
         ])
       )))


(defn generic-modal
  "A generic modal form component. This takes a configuration defining what to display
   and creates a predefined panel around it"
  []

  (let [form-state (rf/subscribe [:form-state])
        form-config (rf/subscribe [:form-config])
        form-data (rf/subscribe [:form-data])
        process-cancel (fn [event]
                         (rf/dispatch [:unset-selected-item])
                         (rf/dispatch [:common/show-details-view-item false]))

        ;process-cancel (fn [event]
        ;                 ;(rf/dispatch [:hide-calendar-modal-clear-form-data])
        ;                 (log/info "cancel")
        ;                 (rf/dispatch [:show-detail-area false])
        ;                 )
        ]

    (fn []
      (let [vali-res (f/validate-form-all @form-config @form-data)
            button-cfg (get-in @form-config [:buttons])
            button-cfg (if (fn? button-cfg)
                         (button-cfg @form-data)
                         button-cfg)
            is-edit-item? (:edit-item @form-state)
            ]

        [re-com/modal-panel
         :class "calendar-item-modal"
         :backdrop-color    "grey"
         :backdrop-opacity  0.4
         :wrap-nicely?      true
         :backdrop-on-click (if is-edit-item? nil process-cancel)
         :attr {
                ;:on-key-press (partial on-key-down process-cancel)
                }
         :child
         [:div.card.generic-modal-detail-area
          [:div.card-header
           (when-not is-edit-item?
             [:span.pull-left.clickable.text-muted
              {:style {:margin-left "-15px"}
               :on-click #(process-cancel nil)}
              [:i.fa.fa-chevron-left {:style {:margin-right "5px"}}]  " "])

           [generic-form-toolbar-buttons @form-state button-cfg]
           (if (fn? (:title @form-config))
             ((:title @form-config) @form-data)
             (:title @form-config))
           [:br] [:small [:i
                          (if (fn? (:title-subtext @form-config))
                            ((:title-subtext @form-config) @form-data)
                            (:title-subtext @form-config))]]]


          [:img.card-img-top]
          [:div.card-body
           [:h4.card-title
            (if (fn? (:card-title @form-config))
              ((:card-title @form-config) @form-data)
              (:card-title @form-config))]

           [:p.card-text "This is a longer card with supporting text below as a natural lead-in to "
            "additional content. This content is a little bit longer."]

           [:p.card-text (:summary @form-data)]
           ;[:p.card-text [:small.text-muted "last updated " (h/format-date "dd.MM.yyyy HH:mm" (tc/from-long (:created @form-data)))]]

           [(:inner-form @form-config)]

           ]

          [:div.card-footer

           [generic-form-buttons-left @form-state button-cfg]
           [generic-form-buttons-right @form-state button-cfg]

           [:div.clearfix]

           [generic-form-remove-confirm @form-state button-cfg]

           ;[:div.pull-right
           ; [:div.btn-group
           ;  [:button.btn.btn-outline-secondary.btn-sm
           ;   {:type     :button
           ;    :on-click process-cancel}
           ;   "Close"]]
           ; ]
           ]]
          ]

        )
       )))

