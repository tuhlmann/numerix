(ns numerix.fields.project-selector
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [numerix.fields.dropdown :as dropdown]
            [numerix.site :as site]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]
            [numerix.api.project :as prj-api]
            [re-frame.core :as rf]))

(defn project-dropdown-content
  [& {:keys [props
             projects
             user-data
             current-project-id
             show-actions
             select-current-href
             select-fn]
      :or {props {}
           show-actions true}}]

  (let [current-project (prj-api/find-project current-project-id @projects)
        other-projects (filter (fn [p] (not= (:_id p) current-project-id)) @projects)]

    [:ul.dropdown-menu (r/merge-props {:role "menu"} props)
     ;[:li.dropdown-header "Main Project"]
     ;(if (= current-project-id root-project-id)
     ;  [:li.dropdown-item [:a {:href (select-current-href root-project)}
     ;                      [:i.fa.fa-check] " " (:name root-project)]]
     ;
     ;  [:li.dropdown-item [:a {:href "Javascript://"
     ;                          :on-click (handler-fn (select-fn root-project))}
     ;                      (:name root-project)]])

     (when current-project
       [:div
        [:li.dropdown-header "Current Project"]
        [:li.dropdown-item [:a {:href (select-current-href current-project)}
                            [:i.fa.fa-check] " " (:name current-project)]]
        [:li.dropdown-divider]])
     (when-not (empty? other-projects)
       [:div
        [:li.dropdown-header "Other Projects"]
        (for [item other-projects]
          ^{:key item} [:li.dropdown-item
                        [:a {:href "Javascript://"
                             :on-click (handler-fn (select-fn item)) }
                         (:name item)]])])
     (when show-actions
       (list
       ^{:key 1}[:li.dropdown-header "Actions"]
       ^{:key 2}[:li.dropdown-item [:a {:href (site/projects-route)} "Create New Project"]]
       ))]))


(defn project-nav-selector [user-data projects]
  (fn [user-data projects]
    (let [current-project-id (:current-project @user-data)
          current-project (prj-api/find-project current-project-id @projects)]

      (comment
        "The :dropdown-toggle-fn does not re-render if the current-project name changes.
        That's because the function is recreated- but the place that uses it
        still uses the old one (that's a guess)")

      [dropdown/dropdown-field
       :orientation :right
       :close-on-outside-clk true
       :dropdown-toggle-fn
       (fn [props]
         [:a.nav-link.dropdown-toggle (r/merge-props {:href "Javascript://" :role "button"} props)
          [:i.fa.fa-cubes] " "
          (:name current-project "No Project!")
          [:span.caret]])

       :dropdown-content-fn
       (fn [props]
         [project-dropdown-content
          :props props
          :current-project-id current-project-id
          :show-actions true
          :projects projects
          :user-data @user-data
          :select-current-href (fn [project] (site/project-route {:projectId (str (:_id project))}))
          :select-fn (fn [project]
                       (rf/dispatch [:project/activate project]))])])))


(defn item-project-switcher [user-data projects item-project-id select-project-fn]
  (fn [user-data projects]
    (let [current-project (prj-api/find-project item-project-id @projects)]

      [dropdown/dropdown-field
       :orientation :right
       :dropdown-toggle-fn
       (fn [props]
         [:a.dropdown-toggle (r/merge-props {:href "Javascript://" :role "button"} props)
          [:i.fa.fa-cubes] " "
          (:name current-project "No Project!")
          [:span.caret]])

       :dropdown-content-fn
       (fn [props]
         [project-dropdown-content
          :props props
          :current-project-id item-project-id
          :show-actions false
          :projects projects
          :user-data user-data
          :select-current-href (fn [project] "Javascript://")
          :select-fn select-project-fn])])))



;(defn project-nav-selector2 [user-data projects]
;  (fn [user-data projects]
;    (let [current-project-id (:current-project user-data)
;          root-project (prj-api/find-root-project @projects)
;          root-project-id (:_id root-project)
;          current-project (prj-api/find-project current-project-id @projects)
;          other-projects (filter (fn [p] (and
;                                           (not= (:_id p) current-project-id)
;                                           (not= (:_id p) root-project-id))) @projects)]
;      [:li.nav-item.dropdown
;       [:a.nav-link.dropdown-toggle {:href "Javascript://" :data-toggle "dropdown" :role "button" :aria-expanded false}
;        [:i.fa.fa-cubes] " "
;        (:name current-project "No Project!")
;        [:span.caret]]
;       [:ul.dropdown-menu.dropdown-menu-right {:role "menu"}
;        [:li.dropdown-header "Main Project"]
;        (if (= current-project-id root-project-id)
;          [:li.dropdown-item [:a {:href (site/project-route {:projectId (str root-project-id)})}
;                              [:i.fa.fa-check] " " (:name root-project)]]
;
;          [:li.dropdown-item [:a {:href "Javascript://"
;                                  :on-click (handler-fn (emit :project/activate root-project))}
;                              (:name root-project)]])
;
;        (when (and current-project
;                   (not= current-project-id root-project-id))
;          [:div
;           [:li.dropdown-header "Current Project"]
;           [:li.dropdown-item [:a {:href (site/project-route {:projectId (.toString current-project-id)})}
;                               [:i.fa.fa-check] " " (:name current-project)]]
;           [:li.dropdown-divider]])
;        (when-not (empty? other-projects)
;          [:div
;           [:li.dropdown-header "Other Projects"]
;           (for [item other-projects]
;             ^{:key item} [:li.dropdown-item
;                           [:a {:href "Javascript://"
;                                :on-click (handler-fn (emit :project/activate item)) }
;                            (:name item)]])])
;        [:li.dropdown-header "Actions"]
;        [:li.dropdown-item [:a {:href (site/projects-route)} "Create New Project"]]]])))
