(ns numerix.views.dashboard
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [taoensso.encore :as enc]
            [taoensso.timbre :as log]
            [secretary.core :refer [dispatch!]]
            [numerix.views.base :as base]
            [re-frame.core :as rf]))

(defn db-card-recent-companies []
 [:div.card
  [:img.card-img-top]
  [:div.card-body
   [:h4.card-title "Recent Companies"]
   [:p.card-text "This is a longer card with supporting text below as a natural lead-in to "
      "additional content. This content is a little bit longer."]
   [:p.card-text [:small.text-muted "Last updated 3 mins ago"]]]])

(defn db-card-recent-projects []
 [:div.card
  [:img.card-img-top]
  [:div.card-body
   [:h4.card-title "Recent Projects"]
   [:p.card-text "This is a longer card with supporting text below as a natural lead-in to "
      "additional content. This content is a little bit longer."]
   [:p.card-text [:small.text-muted "Last updated 3 mins ago"]]]])

(defn db-card-due-invoices []
 [:div.card
  [:img.card-img-top]
  [:div.card-body
   [:h4.card-title "Due Invoices"]
   [:p.card-text "This is a longer card with supporting text below as a natural lead-in to "
      "additional content. This content is a little bit longer."]
   [:p.card-text [:small.text-muted "Last updated 3 mins ago"]]]])

(defn db-card-placeholder []
 [:div.card
  [:img.card-img-top]
  [:div.card-body
   [:h4.card-title "Something Important"]
   [:p.card-text "This is a longer card with supporting text below as a natural lead-in to "
      "additional content. This content is a little bit longer."]
   [:p.card-text [:small.text-muted "Last updated 3 mins ago"]]]])

(defn dashboard-page []
  (rf/dispatch-sync [:init-form-state {}])
  (fn []
    (base/base
     [:div.row {:key (enc/uuid-str 5)}
      [:div.col-md-6.col-12
       [db-card-recent-companies]]
      [:div.col-md-6.col-12
       [db-card-recent-projects]]]
      [:div.row {:key (enc/uuid-str 5)}
       [:div.col-md-6.col-12
        [db-card-due-invoices]]
       [:div.col-md-6.col-12
        [db-card-placeholder]]])))
