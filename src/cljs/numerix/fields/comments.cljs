(ns numerix.fields.comments
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
            [cljs-time.core :as t]))

(def comment-input-min-height 86)

(defn send-comment [comment]
  (when-not (empty? comment)
    (rf/dispatch [:related-comments/send-comment comment])))

(defn find-mention [memberships sub-msg]

  (let [msg (str/lower-case sub-msg)
        special-names ["team"]]

    (if-let [name (first (filter #(str/starts-with? msg %) special-names))]

      {:mention-type :group
       :group-name name}

      (when-let [member (first (filter (fn [m]
                                         (or (str/starts-with? msg (str/lower-case (:__user-name m)))
                                             (str/starts-with? msg (:email m)))) memberships))]

        {:mention-type :person
         :user-id (:user-id member)}

        ))))

(defn find-mentions
  "Scan the msg for '@' followed by (:__user-name member) or (:email member).
  If found add a record to the returned mentions vector.
  loop fromIndex 0
    - find '@' starting at fromIndex
    - check if substring pos+1 .. end startsWith a member user name or email
      - yes, add to result vector
    - recur fromIndex = pos+length found string
  "
  [memberships msg]

  (when-let [mentions
             (loop [mentions []
                    from-idx 0]

               (enc/if-let [pos (str/index-of msg "@" from-idx)
                            _ (> (count msg) (+ pos 2))
                            mention (find-mention memberships (subs msg (inc pos)))]

                           (recur (conj mentions mention) (inc pos))

                           (vec mentions)))]

    (vec
      (reduce (fn [akku mention]
                (case (:mention-type mention)

                  :group
                  (if (empty? (filter #(= (:group-name %) (:group-name mention)) akku))
                    (conj akku mention)

                    akku)

                  :person
                  (if (empty? (filter #(= (:user-id %) (:user-id mention)) akku))
                    (conj akku mention)

                    akku)

                  akku)) nil mentions))))


(defn comment-message [idx mouse-over current-user msg]
  (let [my-msg (= (:author-id msg) (:_id current-user))]
    [:div.comment-container
     {:on-mouse-over (handler-fn (reset! mouse-over idx))
      :on-mouse-leave  (handler-fn (reset! mouse-over -1))
      :class (if (= (:author-id msg) (:_id current-user)) "me" "")}
     [:div.comment-message-lead
      [:img.img-fluid.rounded-circle.comment-author-img
       {
        :src (h/profile-img-src (:__author-profile-img-id msg) (:__author-email msg))
        }]]
     [:div.comment-message
      [:div.comment-message-header
        [:h3 (:__author-name msg)]
        [:span.date (str (h/format-local-date "dd.MM.yyyy HH:mm" (:ts msg))
                         ", "
                         (rel-date/format (:ts msg)))]]
      [:div.message-container
       [markdown/markdown->html (:message msg)]]

      [:div.comment-message-hover-row

       (when my-msg
         [re-com/row-button
          :class "comment-message-hover-btn"
          :md-icon-name    "zmdi zmdi-delete"
          :mouse-over-row? (= @mouse-over idx)
          :tooltip         "Delete this post"
          :on-click (handler-fn (rf/dispatch [:related-comments/remove-comment (:_id msg)]))])
       ]

      ]]
    ))


(defn comment-input []
  (let [memberships (rf/subscribe [:memberships-sort-by-name])
        comment-input (r/atom "")
        send-message-fn (fn []
                          (enc/when-let [msg @comment-input
                                         _ (seq (str/trim msg))]
                                        (let [mentions (find-mentions @memberships @comment-input)]
                                          (send-comment (merge {:message msg}
                                                               (when-not (empty? mentions)
                                                                      {:mentions mentions})))
                                          (reset! comment-input ""))))
        cancel-message-fn (fn []
                            (reset! comment-input ""))
        ]

    (r/create-class
      {:display-name
       "comments-input-area"

       :component-will-update
       (fn [this]
         (let [;p (r/dom-node this)
               ; FIXME How get the child dom node from r/dom-node
               textarea (.getElementById js/document "comments-input-area")]
           ($! textarea :style.height "")
           (if (or (nil? @comment-input) (empty? @comment-input))
             ($! textarea :style.height (str comment-input-min-height "px"))
             ($! textarea :style.height (str (max comment-input-min-height (min (.-scrollHeight textarea) 200)) "px"))
             )
           ))

       :reagent-render
       (fn []
         (let [markup
               [:div.comments-input-area-container
                [:div.form-group
                 [:div
                  [:textarea#comments-input-area.form-control.comments-input-area
                   {:type "text"
                    :style {:height (str comment-input-min-height "px")}
                    :value @comment-input
                    :placeholder "Write your comment here"
                    :on-change (fn [v]
                                 (reset! comment-input (.-target.value v)))
                    }]
                  [:div
                   [:button.pull-right.btn.btn-sm.btn-primary
                    {:type     :button
                     :style {:margin-left "20px"}
                     :on-click (fn [] (send-message-fn))}
                    " Comment"]
                   [:button.pull-right.btn.btn-sm.btn-secondary
                    {:type     :button
                     :on-click (fn [] (cancel-message-fn))}
                    " Cancel"]
                   ]

                  ]]]

               atWhoElem (js/$ (str "#comments-input-area"))

               _ (.atwho atWhoElem
                         (clj->js {:at \@
                                   :displayTpl "<li data-name='${name}'>${name} <small>${email}</small></li>"
                                   :insertTpl "@${name}, "
                                   :data (into [{:name "Team" :email ""}]
                                               (mapv (fn [it] {:name (:__user-name it) :email (:email it)})  @memberships))
                                   }))
               ]

           (.on atWhoElem "shown.atwho" (fn [event]
                                          ;(log/info "shown.atwho, return enabled false")
                                          (rf/dispatch [:mouse-inside-detail-area-no-follow true])))

           (.on atWhoElem "hidden.atwho" (fn [event]
                                           ;(log/info "hidden.atwho, return enabled true")
                                           (rf/dispatch [:mouse-inside-detail-area-no-follow false])
                                           ))

           (.on atWhoElem "inserted.atwho" (fn [jqEvent obj event]
                                             ;(log/info "set comment input " (.val atWhoElem))
                                             (reset! comment-input (.val atWhoElem))
                                             (.trigger (.-event js/$) (clj->js {:type :keypress
                                                                                :which (.charCodeAt \A)
                                                                                }))
                                             ))

           markup))
       })))


(defn comments-container []
  (let [form-state (rf/subscribe [:form-state])
        form-data (rf/subscribe [:form-data])
        memberships (rf/subscribe [:memberships])
        comments (rf/subscribe [:related-comments (:form-name @form-state) (:_id @form-data)])
        user (rf/subscribe [:current-user])
        mouse-over (r/atom -1)]

    (fn []
      (let [messages (mapv (fn [msg]
                             (if-not (:__author-name msg)
                               (if-let [m (first (filter #(= (:user-id %) (:author-id msg)) @memberships))]
                                 (merge msg {:__author-name (:__user-name m)
                                             :__author-email (:email m)
                                             :__author-profile-img-id (:__user-profile-img-id m)})
                                 msg)
                               msg)) (:messages @comments))]

        [:div#comments-container-wrapper
         [:label.form-control-label
          {:style {:padding 0
                   :margin 0}}
          "Comments:"]
         [:div#comments-container
          [:div#comments-section
           [:div.comments-list
            (doall
              (for [[idx msg] (map-indexed vector messages)]
                (do
                  ^{:key idx}
                  [comment-message idx mouse-over @user msg])))]

           [:div.comments-footer
            [comment-input]]]
          ]]
        ))))
