(ns numerix.fields.chat-room
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

(def chat-input-min-height 56)

(defn send-chat-message [chat-message]
  (when-not (empty? chat-message)
    (rf/dispatch [:chat/send-message chat-message])))

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

(defn on-key-down [return-enabled send-message-fn event]
  ;(log/info "on-key-down, return enabled is " @return-enabled)
  (cond

    (and
      @return-enabled
      (= (.-keyCode event) (:ENTER d/keycodes))
      (not (.-shiftKey event)))
    (do
      (.preventDefault event)
      (send-message-fn))

    (and
      (not @return-enabled)
      (= (.-keyCode event) (:ENTER d/keycodes))
      (not (.-shiftKey event)))
    (reset! return-enabled true)

    )
  ;(reset! return-enabled true)
  )


(defn chat-message [idx mouse-over current-user msg]
  (let [my-msg (= (:author-id msg) (:_id current-user))]
    [:div.chat-message-container
     {:on-mouse-over (handler-fn (reset! mouse-over idx))
      :on-mouse-leave  (handler-fn (reset! mouse-over -1))
      :class (if (= (:author-id msg) (:_id current-user)) "me" "")}
     [:div.chat-message-lead
      [:img.img-fluid.rounded-circle.chat-author-img
       {
        :src (h/profile-img-src (:__author-profile-img-id msg) (:__author-email msg))
        }]]
     [:div.chat-message
      [:h3 (:__author-name msg)]
      [:span.date (str (h/format-local-date "dd.MM.yyyy HH:mm" (:ts msg))
                       ", "
                       (rel-date/format (:ts msg)))]
      [:div.message-container
       [markdown/markdown->html (:message msg)]]

      [:div.chat-message-hover-row

       (when my-msg
         [re-com/row-button
          :class "chat-message-hover-btn"
          :md-icon-name    "zmdi zmdi-delete"
          :mouse-over-row? (= @mouse-over idx)
          :tooltip         "Delete this post"
          :on-click (handler-fn (rf/dispatch [:chat/remove-message (:_id msg)]))])
       ]

      ]]
  ))


(defn chat-input []
  (let [memberships (rf/subscribe [:memberships-sort-by-name])
        chat-input (r/atom "")
        return-enabled (r/atom true)
        send-message-fn (fn []
                          (enc/when-let [msg @chat-input
                                         _ (seq (str/trim msg))]
                            (let [mentions (find-mentions @memberships @chat-input)]
                              (send-chat-message (merge {:message msg}
                                                        (when-not (empty? mentions)
                                                          {:mentions mentions})))
                              (reset! chat-input "")
                              (.val (js/$ (str "#chat-input-area")) ""))))]

    (r/create-class
      {:display-name
       "chat-input-area"

       :component-will-update
       (fn [this]
         (let [;p (r/dom-node this)
               ; FIXME How get the child dom node from r/dom-node
               textarea (.getElementById js/document "chat-input-area")]
           ($! textarea :style.height "")
           (if (or (nil? @chat-input) (empty? @chat-input))
             ($! textarea :style.height (str chat-input-min-height "px"))
             ($! textarea :style.height (str (max chat-input-min-height (min (.-scrollHeight textarea) 200)) "px"))
             )
           ))

       :reagent-render
       (fn []
         (let [markup
               [:div.chat-input-area-container
                [:div.form-group
                 [:div.input-group
                  ;[chat-input-area
                  ; :id "chat-input-area"
                  ; :class "form-control chat-input-area"
                  ; :style {:height (str chat-input-min-height "px")}
                  ; :value chat-input
                  ; :placeholder "Write your message here"
                  ; :on-key-down (partial on-key-down chat-input)
                  ; :on-change (fn [v]
                  ;              (reset! chat-input v))
                  ; ]
                  [:textarea#chat-input-area.form-control.chat-input-area
                   {:type "text"
                    :style {:height (str chat-input-min-height "px")}
                    :value @chat-input
                    :placeholder "Write your message here"
                    :on-key-down (partial on-key-down return-enabled send-message-fn)
                    :on-change (fn [v]
                                 (reset! chat-input (.-target.value v)))
                    }]
                  [:input-group-btn
                   (if (empty? @chat-input)
                     [:div.image-upload.pull-right.btn.btn-outline-primary
                      [:label {:for "file-input"
                               :style    {:padding "15px"}}
                       [:i.fa.fa-cloud-upload]]
                     [:input#file-input {:type "file"
                                         :style{:display "none"}}]]
                     [:button.pull-right.btn.btn-outline-primary
                      {:type     :button
                       :style    {:padding "15px"}
                       :on-click (fn [] (send-message-fn))}
                      [:i.fa.fa-paper-plane]
                      ]
                     )
                   ]

                  ]]]

               atWhoElem (js/$ (str "#chat-input-area"))

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
                                          (rf/dispatch [:mouse-inside-detail-area-no-follow true])
                                          (reset! return-enabled false)))

           (.on atWhoElem "hidden.atwho" (fn [event]
                                           ;(log/info "hidden.atwho, return enabled true")
                                           (rf/dispatch [:mouse-inside-detail-area-no-follow false])
                                           (js/setTimeout #(reset! return-enabled true) 200)
                                           ))

           (.on atWhoElem "inserted.atwho" (fn [jqEvent obj event]
                                             ;(log/info "set chat input " (.val atWhoElem))
                                             (reset! chat-input (.val atWhoElem))
                                             (.trigger (.-event js/$) (clj->js {:type :keypress
                                                                                 :which (.charCodeAt \A)
                                                                                 }))
                                             ))

           markup))
       })))


(defn present-user [this-user member]
  (let [form-state (rf/subscribe [:form-state])]
    (fn [this-user member]
      (let [form-name (:form-name @form-state)
            selected-item-id (get-in @form-state [:selected-item :_id])
            user-name (:__user-name member)
            me? (= (:user-id member) (:_id this-user))
            user-online? (= (:__online-status member) :online)
            user-here? (some (fn [p]
                               (and (= (:related-type p) form-name)
                                    (= (:related-id p) selected-item-id)
                                    )) (:__presences member))]

        [:button {:type :button
                  :class (str "btn btn-link "
                              (cond
                                me? "online-here"

                                user-here?  "online-here"

                                user-online? "online-somewhere"

                                :else ""))
                  :on-click (handler-fn (log/info "pingUser(user)"))
                  :title (str "Click to call out " user-name)}
         user-name]
      ))))

;(defn- find-membership-presence [presences user form-data member]
;  (enc/if-let [user-presences (filter #(=(:user-id %) (:user-id member)) presences)
;               _ (seq user-presences)]
;
;              (if-let [here-presence (first (filter #(= (:selected-item-id %) (:_id form-data)) user-presences))]
;                (-> member
;                    (assoc :presence here-presence)
;                    (assoc :location :here))
;
;                (-> member
;                    (assoc :presence (first user-presences))
;                    (assoc :location :online)))
;
;              (-> member
;                  (assoc :location :offline))
;              ))

(defn present-users [user]
  (let [memberships (rf/subscribe [:memberships-sort-by-name])]

    (fn [user]
      [:div.online-status
       [:ul
        (doall
          (for [[idx member] (map-indexed vector @memberships)]
            ^{:key idx}
            [:li [present-user user member]]))
        ]])))

(defn chat-room [chat-room-2]
  (let [form-data (rf/subscribe [:form-data])
        memberships (rf/subscribe [:memberships])
        chat-room (rf/subscribe [:chat-room-msg-cache (:_id @form-data)])
        user (rf/subscribe [:current-user])
        fullscreen? (r/atom false)
        mouse-over (r/atom -1)]

    (fn []
      (let [messages (mapv (fn [msg]
                             (if-not (:__author-name msg)
                               (if-let [m (first (filter #(= (:user-id %) (:author-id msg)) @memberships))]
                                 (merge msg {:__author-name (:__user-name m)
                                             :__author-email (:email m)
                                             :__author-profile-img-id (:__user-profile-img-id m)})
                                 msg)
                               msg)) (:messages @chat-room))]

        [:div#chat-room-container-wrapper
         {:class (if @fullscreen? "full-screen" "")}
         ;[:button.btn.btn-link.bt-sm.pull-right
         ; {:type :button
         ;  :on-click (handler-fn
         ;              (reset! fullscreen? (not @fullscreen?)))}
         ; [:i.fa.fa-arrows-alt]]

         [:div#chat-room-container
          [:div#chat-room
           [scroll/autoscroll-list
            {
             :scroll? true
             :class   "chat-message-list"
             :on-scroll-start (fn [e]
                                (when (= (:status @chat-room) :idle)
                                  (let [m (first messages)]
                                    (log/info "scrolled to top, can load new data")
                                    (rf/dispatch [:chat/load-messages {:chat-room-id (:chat-room-id m)
                                                                       :last-message-ts (:ts m)
                                                                       :last-message-id (:_id m)}])
                                  )))
             }
            (doall
              (for [[idx msg] (map-indexed vector messages)]
                (do
                  ;(log/info "msg is " msg)
                  ^{:key idx}
                  [chat-message idx mouse-over @user msg])))]

           [:div.chat-room-footer
            [chat-input]]]
         [:div#user-presence-container
          [present-users @user]]
         ]]
      ))))
