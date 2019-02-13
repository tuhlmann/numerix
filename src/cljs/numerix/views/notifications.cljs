(ns numerix.views.notifications
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [numerix.state :as s]
            [taoensso.timbre :as log]
            [numerix.site :as site]
            [numerix.fields.flyout :as flyout]
            [cljs-time.core :refer [now days minus day-of-week]]
            [cljs-time.format :refer [formatter formatters parse unparse]]
            [clojure.string :as str]
            [re-com.core :refer [row-button single-dropdown v-box h-box md-circle-icon-button
                                 datepicker datepicker-dropdown]
             :refer-macros [handler-fn]]
            [re-frame.core :as rf :refer [dispatch]]
            [numerix.lib.helpers :as h]
            [numerix.fields.markdown-render :as markdown]
            [re-com.core :as re-com]
            [goog.date.relative :as rel-date]
            [numerix.lib.route-helpers :as route-helpers]))


(defn notification [idx mouse-over notifi]
  (let [unread? (= (:status notifi) :unread)]
    [:li.notification-container.clickable
     {:on-mouse-over  (handler-fn (reset! mouse-over idx))
      :on-mouse-leave (handler-fn (reset! mouse-over -1))
      :on-click       (fn []
                        (rf/dispatch [:notification/close-notifications-widget])
                        (rf/dispatch [:navigate-to (route-helpers/make-chat-route notifi)]))
      :class          (if unread? "unread" "read")}
     [:div.notification-lead
      [:img.img-fluid.rounded-circle.notification-author-img
       {
        :src (h/profile-img-src (:__author-profile-img-id notifi) (:__author-email notifi))
        }]]
     [:div.notification-message
      [:h3 (:__author-name notifi)]
      [:span.date (str (h/format-local-date "dd.MM.yyyy HH:mm" (:ts notifi))
                       ", "
                       (rel-date/format (:ts notifi)))]
      [:div.message-container
       [markdown/markdown->html (:__message notifi)]]

      [:div.notification-message-hover-row

       (when unread?
         [re-com/row-button
          :class "notification-hover-btn"
          :md-icon-name    "zmdi zmdi-check"
          :mouse-over-row? (= @mouse-over idx)
          :tooltip         "Mark as read"
          :on-click (handler-fn (rf/dispatch [:notification/mark-as-read notifi]))])
       ]

      ]]
    ))

(defn notifications-widget []
  (let [memberships (rf/subscribe [:memberships])
        notifi-sub (rf/subscribe [:notifications])
        mouse-over (r/atom -1)]

    (fn []
      (let [notifications
            (mapv (fn [notifi]
                    (if-not (:__author-name notifi)
                      (if-let [m (first (filter #(= (:user-id %) (:author-id notifi)) @memberships))]
                        (merge notifi {:__author-name (:__user-name m)
                                       :__author-email (:email m)
                                       :__author-profile-img-id (:__user-profile-img-id m)})
                        notifi)
                      notifi)) @notifi-sub)]

        [flyout/flyout
         {:class "notifications-widget"
          :close-flyout-fn (handler-fn (rf/dispatch [:notification/toggle-notifications-widget]))
          :header-items [:span.pull-right
                         [:span.as-link
                          {
                           :on-click (handler-fn (rf/dispatch [:notification/mark-all-as-read]))
                           :style {:margin-right "10px"}
                           } [:i.fa.fa-check-circle-o.fa-1] " all read"]
                         [:span " "]
                         [:span.as-link
                          {
                           :on-click (handler-fn (do
                                                   (rf/dispatch [:notification/remove-all])
                                                   (rf/dispatch [:notification/toggle-notifications-widget])))
                           :style {:margin-right "10px"}
                           } [:i.fa.fa-trash.fa-1] " remove all"]
                         ]
          }

         [:ul.notification-list
          (doall
            (for [[idx n] (map-indexed vector notifications)]
              ^{:key idx}
              [notification idx mouse-over n])
            )
          ]]))))

