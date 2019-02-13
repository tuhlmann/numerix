(ns numerix.views.knowledgebase
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [re-frame.db :as db]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.views.base :as base]
            [numerix.fields.quill :as quill]
            [numerix.events.common]
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
            [re-frame.core :as rf]))


(defn knowledgebase-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Title" :value (:name @form-data)]
       [f/static-field :type :quill :value (:text @form-data)]
       [f/static-field :label "Tags" :type :tag :value (:tags @form-data)]
       ])))

#_(def t [
        {:id "Cat A"
         :type "foo"
         :title "My first category"
         :items ["V a" "V b" "V c" "Value 1" "Value 2" "Value 3"]}
        ;{:id "Cat B"
        ; :type "bar"
        ; :title "My second category"
        ; :items ["itsy" "babsy" "milli" "mini"]}
        ])

#_(def t [{:id "Cat A"
         :type "foo"
         :title "My first category",
         :items [{:_id "1a" :label "V a"} {:_id "2a" :label "V b"} {:_id "3a" :label "V c"}],
         :single false}
        {:id "Category B"
         :type "bar"
         :title "My second category",
         :items [{:_id "1b" :label "Value 1"} {:_id "2b" :label "Value 2"} {:_id "3b" :label "Value 3"}],
         :single false}
        ])

(defn knowledgebase-detail-edit-inner []
  (let [form-data (rf/subscribe [:form-data])
        ;; FIXME: Subscribe to project-tags
        project-tags (tag-api/list-project-tags)]
    (fn []
      [:div.form
       [f/pfield :label "Title" :path :name]
       [f/pfield :path :text :type :quill]
       [f/pfield :label "Tags" :path :tags :type :tag :categories project-tags]
       ])))


(defn knowledgebase-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        knowledge-entry (if (:add-new @form-state) (crud-ops/new-record @user) (:selected-item @form-state {}))
        form-config {:mode       :edit
                     :title      [:span "Add Knowledge Base Entry"]
                     :inner-form knowledgebase-detail-edit-inner
                     :form-data  knowledge-entry
                     :validation (v/validation-set
                                   (v/length-of :name :within (range 3 200) :blank-message "Please provide a title"))

                     :buttons {
                               :cancel {
                                        :tt      "Reset Knowledge Base Entry"
                                        :handler (fn [_]
                                                   (rf/dispatch [:common/show-details-view-item false]))}

                               :save   {
                                        :tt      "Save Knowledge Base Entry"
                                        :handler (fn [form-data]
                                                   (rf/dispatch [:crud-ops/save {:type :knowledgebase
                                                                                 :record form-data}]))
                                        }

                               :project-switch {:tt "Switch Project"
                                                :handler (fn [form-data project]
                                                           (rf/dispatch [:crud-ops/switch-project
                                                                         {:type :knowledgebase
                                                                          :record form-data
                                                                          :project project}])) }

                               :remove {
                                        :tt      "Remove Knowledge Base Entry"
                                        :handler (fn [form-data]
                                                   (rf/dispatch [:crud-ops/remove {:type :knowledgebase
                                                                                   :record form-data}]))
                                        }
                               }}]




    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))

(defn knowledgebase-detail-view [item]
  (let [form-config {:mode :view
                     :title         [:span "Knowledge Base"]
                     :title-subtext (fn [i]
                                      [:span [:span "View Knowledgebase"]
                                       [:span.pull-right
                                        [tag/tag-list-mini (:tags i)]]])

                     ;:card-title    Do not define a title
                     :inner-form    knowledgebase-detail-view-inner
                     :form-data     item
                     :buttons       {
                                     :project-view {:tt "Current Project"}

                                     :edit {
                                            :tt "Edit Knowledge Base Entry"
                                            :handler (fn [_]
                                                       (rf/dispatch [:common/show-details-edit-item true])
                                                       )
                                            }

                                     :remove {
                                              :tt "Remove Knowledge Base Entry"
                                              :handler (fn [form-data]
                                                         (log/info "Remove record ")
                                                         (rf/dispatch [:crud-ops/remove {:type :knowledgebase
                                                                                         :record form-data}]))}}}]




    (rf/dispatch-sync [:init-form-config form-config])
    (fn [_]
      [c/generic-form])))

(defn knowledgebase-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [knowledgebase-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [knowledgebase-detail-view list-item]
          [c/no-item-found "Knowledge Base"])

        :else
        [:div "don't know what to do "]))))


;;; Main entry for company master-detail page
(defn knowledgebase-page []
  (let [page-config
        {:form-state    {:form-name         :knowledgebase
                         :edit-item         false
                         :show-details      false
                         :is-loading        true
                         :mouse-inside-detail-area-no-follow false
                         :on-outside-clickx (fn [form-state]
                                              (when (and (:show-details form-state)
                                                         (v/valid? (s/validation-errors form-state)))
                                                (let [orig (:original-record form-state)
                                                      form-data (rf/subscribe [:form-data])
                                                      diffed (clojure.data/diff orig @form-data)
                                                      has-changes (or (not (nil? (first diffed)))
                                                                      (not (nil? (second diffed))))]
                                                  (if has-changes
                                                    (do
                                                      (rf/dispatch [:common/detail-only-view false])
                                                      (rf/dispatch [:knowledgebase/save form-data]))
                                                    (rf/dispatch :common/show-details-view-item false)))))}

         :master-config {
                         :full-text-fn                    (fn [i] (str (str/lower-case (:name i)) " "
                                                                       (str/lower-case (:text i ""))))
                         :inner-master-list-item           c/master-list-item-std
                         :inner-master-list-item-desc      {
                                                            :title    (fn [v] [:div
                                                                               [:div.col-12.col-sm-4.col-lg-3
                                                                                (:name v)]
                                                                               [:div.col-12.col-sm-8.col-lg-9
                                                                                (h/shorten 100 "..." (cue/strip-tags (:text v)))]])

                                                            :img-icon "fa fa-lightbulb-o"
                                                            ;:on-click c/select-item-and-edit-details
                                                            }

                         :master-list-item-std-detail-link #(site/knowledge-entry-route {:knowledge-entry-id (.toString (:_id %))})
                         :master-list-link                 #(site/knowledgebase-route)
                         :detail-area-main                 knowledgebase-detail-area
                         }}]

    (rf/dispatch-sync [:init-knowledgebase-page page-config])
    (fn []
      [c/master-detail-page])))


;; Initial knowledgebase page state
(rf/reg-event-fx
  :init-knowledgebase-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    {:db (-> db
             (s/assoc-master-config master-config)
             (s/assoc-form-state (merge (s/form-state db) form-state)))

     :load-and-cache [:knowledgebase/list 1000]}))

