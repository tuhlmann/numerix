(ns numerix.views.projects
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [taoensso.encore :as enc]
            [taoensso.timbre :as log]
            [numerix.views.common :as c]
            [numerix.views.fields :as f]
            [numerix.api.project :as project-api]
            [numerix.lib.helpers :as h]
            [numerix.site :as site]
            [numerix.events.project]
            [numerix.events.common]
            [clojure.string :as str]
            [validateur.validation :as v]
            [numerix.lib.datatypes :as d]
            [re-frame.db :as db]
            [re-frame.core :as rf]))


(defn project-detail-view-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/static-field :label "Title"   :value (:name @form-data)]
       [f/static-field :label "Summary" :value (:summary @form-data)]])))

(defn project-detail-edit-inner []
  (let [form-data (rf/subscribe [:form-data])]
    (fn []
      [:form
       [f/pfield :label "Title"    :path :name]
       [f/pfield :label "Summary"  :path :summary :type :textarea :rows 5]
       ])))

(defn project-detail-edit []
  (let [form-state (rf/subscribe [:form-state])
        user (rf/subscribe [:current-user])
        prj-data (if (:add-new @form-state) (project-api/new-project-rec @user) (:selected-item @form-state {}))
        form-config {:mode          :edit
                     :title         [:span "Add Project"]
                     :title-subtext [:span "Add a new Project"]
                     :card-title    [:span "Project " (:name prj-data)]
                     :inner-form    project-detail-edit-inner
                     :form-data     prj-data
                     :validation    (v/validation-set
                                      (v/length-of :name :within (range 3 200) :blank-message "Please provide a project name"))

                     :buttons {
                               :cancel {
                                        :tt      "Reset Project Data"
                                        :handler (fn [_]
                                                   (rf/dispatch [:common/show-details-view-item]))
                                        }
                               :save   {
                                        :tt      "Save Project Data"
                                        :handler (fn [form-data]
                                                   (rf/dispatch [:crud-ops/save {:type :project
                                                                         :record form-data}]))
                                        }
                               }
                     }]

    (rf/dispatch-sync [:init-form-config form-config])
    (fn []
      [c/generic-form])))


(defn project-detail-view [item]
  (let [user (rf/subscribe [:current-user])
        form-config {:mode :view
                     :title         [:span "Project"]
                     :title-subtext [:span "View Project"]
                     :card-title    [:span "Project"]
                     :inner-form project-detail-view-inner
                     :form-data item
                     :buttons-all {
                                   :edit {
                                          :tt      "Edit Project"
                                          :handler (fn [_]
                                                     (rf/dispatch [:common/show-details-edit-item true]))
                                          }
                                   }

                     :buttons-not-current (fn [prj]
                                            {
                                             :activate {
                                                        :tt      "Make this project active"
                                                        :handler (fn [form-data]
                                                                   (rf/dispatch [:project/activate form-data]))}

                                             :remove {
                                                      :tt "Remove Project"
                                                      :handler (fn [form-data]
                                                                 (rf/dispatch [:crud-ops/remove {:type :project
                                                                                         :record form-data}]))
                                                      }
                                             })
                     }
        ]

    (rf/dispatch-sync [:init-form-config form-config])

    (fn [item]
      (let [usr-prj-id (:current-project @user)
            buttons (if-not (= usr-prj-id (:_id item))
                      (merge (:buttons-all form-config) ((:buttons-not-current form-config) item))

                      (:buttons-all form-config))
            fc (assoc form-config :buttons buttons)]
        (rf/dispatch-sync [:init-form-config fc])
        [c/generic-form]))))


(defn project-detail-area []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (cond
        (:edit-item @form-state)
        [project-detail-edit]

        (:show-details @form-state)
        (if-let [list-item (:selected-item @form-state)]
          [project-detail-view list-item]
          [c/no-item-found "Project"])

        :else
        [:div]))))


;;; Main entry for project master-detail page
(defn projects-page []
  (let [;projects (r/cursor db/app-db [:projects])
        user (rf/subscribe [:current-user])
        page-config
        {:form-state {
                      :form-name :projects
                      :edit-item false
                      :show-details false
                      :mouse-inside-detail-area-no-follow false
                      }
         :master-config {
                       :inner-master-list-item c/master-list-item-std
                       :inner-master-list-item-desc {
                                                     :title (fn [v]
                                                              (let [usr-prj-id (:current-project @user)]
                                                                (if (= usr-prj-id (:_id v))
                                                                  [:h5 {:style {:text-decoration :underline}}
                                                                   (str "Current: " (:name v))]
                                                                  [:h6 (str (:name v))])))
                                                     :description (fn [v] [:span (:summary v)])
                                                     :img-icon (fn [v]
                                                                 (let [usr-prj-id (:current-project @user)]
                                                                   ;; FIXME: Check not for root project
                                                                   ;; but for shared prj, give different icon
                                                                   (cond
                                                                   (= usr-prj-id (:_id v)) "fa fa-home fa-2x"
                                                                   (:is-root-project v) "fa fa-anchor fa-2x"
                                                                   :else "fa fa-cubes fa-2x")))

                                                     }

                       :master-list-item-std-detail-link #(site/project-route {:projectId (.toString (:_id %))})
                       :master-list-link #(site/projects-route)
                       :detail-area-main project-detail-area
                       :sort-items-fn (fn [items]
                                        ;(let [grouped (group-by
                                        ;                #(if (:is-root-project %)
                                        ;                  :root
                                        ;                  :child) items)]
                                          (into [] (sort-by :name items)))
                       }}]

    (rf/dispatch-sync [:init-projects-page page-config])
    (fn []
      [c/master-detail-page])))


(rf/reg-event-fx
  :init-projects-page
  (fn [{db :db} [_ {:keys [form-state master-config] :as page-config}]]
    (let [master-data (:projects db)
          new-db (-> db
                     (s/assoc-master-config (assoc master-config :master-data master-data))
                     (s/assoc-form-state (merge (s/form-state db) form-state))
                     (s/assoc-in-form-state :selected-item (c/find-selected-item master-data)))
          new-db (if (some? (s/get-in-form-state new-db :selected-item))
                   (s/assoc-in-form-state new-db :show-details true)
                   new-db)]

      {:db new-db})))

