(ns numerix.views.base
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [reagent.interop :refer-macros [$ $!]]
            [numerix.ws :as socket]
            [numerix.state :as s]
            [numerix.history :as hist]
            [numerix.site :as site]
            [numerix.api.user :as user-api]
            [numerix.api.project :as prj-api]
            [numerix.events.project]
            [numerix.events.notification]
            [numerix.lib.helpers :as h]
            [numerix.lib.gravatar :as gravatar]
            [numerix.fields.project-selector :as prj-sel]
            [taoensso.timbre :as log]
            [secretary.core :refer [dispatch!]]
            [cuerdas.core :as str]
            [taoensso.encore :as enc]
            [validateur.validation :as v]
            [numerix.history :as history]
            [numerix.views.common-controls :as ctrl]
            [re-com.core :refer-macros [handler-fn]]
            [re-frame.db :as db]
            [re-frame.core :as rf]
            [numerix.lib.roles :as roledef]
            [numerix.views.notifications :as notifications]
            [numerix.fields.dropdown :as dropdown]))


(defn small-sidebar? [state]
  (= (:sidebar-size-cls state) "sidebar-small"))


;(defn toggle-sidebar-sm [state]
;  (let [window-state (rf/subscribe [:get-window-state])]
;    (when (< (:window-width @window-state) (:sm site/media-breakpoints))
;      (toggle-sidebar state false))))

(defn active-if-route [route]
  (if (hist/current-route? route) "active" ""))


(defn user-menu
  "Creates the user menu.
  orientation is :left or :right. :right will right align the dropdown"
  [user-data orientation]
  (fn [user-data]
    [dropdown/dropdown-field
     :orientation orientation
     :close-on-outside-clk true
     :dropdown-toggle-fn
     (fn [props]
       [:a.nav-link.dropdown-toggle (r/merge-props {:href "Javascript://" :role "button"} props)
        [:img.img-fluid.rounded-circle
         {:src (h/profile-img-src (:profile-image-id @user-data) (:email @user-data))
          :style {:max-height "1.6rem"}}] " "
        [:span.caret]])

     :dropdown-content-fn
     (fn [props]
       [:ul.dropdown-menu (r/merge-props {} props)
        [:li.dropdown-header (:email user-data)]
        [:li.dropdown-item [:a {:href (site/password-route)} [:i.fa.fa-user-secret.fa-fw] " Change Password"]]
        [:li.dropdown-item [:a {:href (site/settings-route)} [:i.fa.fa-cogs.fa-fw] " Settings"]]
        [:li.dropdown-divider]
        [:li.dropdown-header "Project Settings"]
        [:li.dropdown-item [:a {:href (site/projects-route)} [:i.fa.fa-cubes.fa-fw] " Projects"]]

        (when @(rf/subscribe [:has-membership-permission "project-admin:read"])
          [:li.dropdown-item [:a {:href (site/memberships-route)} [:i.fa.fa-users.fa-fw] " Project Members"]])

        (when @(rf/subscribe [:form-data-allowed-read :invoices])
          (list
            ^{:key "li-in-div"}[:li.dropdown-divider]
            ^{:key "li-in-set"}[:li.dropdown-header "Invoice Settings"]
            ^{:key "li-in-drop"}[:li.dropdown-item [:a {:href (site/textblock-route)} [:i.fa.fa-comments-o.fa-fw] " Text Blocks"]]))
        (when (h/isAdmin @user-data)
          (list
            ^{:key "um-a-div"}[:li.dropdown-divider]
            ^{:key "um-a-h"}[:li.dropdown-header "Admin"]
            ^{:key "um-a-item"}[:li.dropdown-item [:a {:href "/admin/users"} "Users"]]))
        [:li.dropdown-divider]
        [:li.dropdown-header "Actions"]
        [:li.dropdown-item [:a {:href "/logout"} [:i.fa.fa-sign-out.fa-fw] " Logout"]]])]))

(defn snackbar-container [& body]
  (when body
    [:div#snackbar-container.in-app
     (into [:div] body)]))

(defn alert-box []
  (let [alerts (rf/subscribe [:alerts])]
    (fn []
      (when-not (empty? @alerts)
        (when-let [smallest-timeout (reduce (fn [a b] (if (< a b) a b))
                                            (filter #(not(nil? %)) (map :timeout @alerts)))]
          (js/setTimeout #(rf/dispatch [:reduce-alert-timeouts smallest-timeout]) (* smallest-timeout 1000)))

        [:div#snackbar-container.in-app
          (for [alert @alerts]
            (let [msg (:msg alert)
                  type (:type alert)
                  msg-arr (if (string? msg) [msg]
                                            (flatten (mapv #(into [] %) (vals msg))))]
              (for [m msg-arr]
                [:div.snackbar.snackbar-with-close.snackbar-opened.animated.fadeIn {:key m
                                                                                    :class (name type)}
                 [:span.snackbar-content
                  [:span.snackbar-close {:on-click #(rf/dispatch [:common/remove-alert-by-id (:alert-id alert)])}
                   [:i.fa.fa-close]] m]])))]))))

(defn loader-spinner []
  (let [form-state (rf/subscribe [:form-state])]
    (fn []
      (when (:is-loading @form-state false)
        [:div.snackbar.snackbar-opened.snackbar-padding-smaller
         [:span.snackbar-content
          [:i.fa.fa-spinner.fa-spin.fa-2x]
          [:span.snackbar-loader (h/unescape "&nbsp;&nbsp;Loading...")]]]))))


;(defn wide-screen-top-navbar [view-state user-data projects]
;  [:div.collapse.navbar-expand-sm
;   [:a.navbar-brand.sidebar-toggler {:href "Javascript://"
;                                     :on-click #(rf/dispatch [:common/toggle-sidebar true])}
;    (h/unescape "&#9776;")]
;
;   [:a.navbar-brand. {:href (site/home-route) :style {:margin-left "1rem"}} " Numerix"]
;   [:ul.nav.navbar-nav
;    [:li.nav-item {:class (active-if-route (site/home-route))}
;     [:a.nav-link {:href (site/home-route)} [:i.fa.fa-home] " Home "]]]
;
;   [:ul.nav.navbar-nav.pull-right
;    [:li.nav-item.dropdown
;     [prj-sel/project-nav-selector user-data projects]]
;    [:li.nav-item {:class (active-if-route (site/contact-route))}
;     [:a.nav-link {:href (site/contact-route)} [:i.fa.fa-info-circle] " Contact"]]
;    [:li.nav-item {:class (active-if-route (site/imprint-route))}
;     [:a.nav-link {:href (site/imprint-route)} [:i.fa.fa-legal] " Imprint"]]
;    [user-menu user-data :right]]])



;(defn small-screen-top-navbar [view-state user-data]
;  [:div#navbar.collapse.d-none.navbar-toggleable-xs
;   [:div.bg-inverse.p-a {:style {:width "60%"}}
;    [:ul.nav.navbar-nav
;     [:li.nav-item.collapsible-menu-item {:class (active-if-route (site/home-route))}
;      [:a.nav-link {:href (site/home-route)} [:i.fa.fa-home] " Home "]]
;     [:li.nav-item.collapsible-menu-item {:class (active-if-route (site/contact-route))}
;      [:a.nav-link {:href (site/contact-route)} [:i.fa.fa-info-circle] " Contact"]]
;     [:li.nav-item.collapsible-menu-item {:class (active-if-route (site/imprint-route))}
;      [:a.nav-link {:href (site/imprint-route)} [:i.fa.fa-legal] " Imprint"]]
;     [user-menu user-data :left]]]])


(defn sidebar [view-state form-state]
  [:div#sidebar-wrapper.hidden-print
   [:img.hidden-sb-small {:src   "/img/sidebar_img.jpg"
                          :style {:max-width "180px"}}]
   [:ul.sidebar-nav
    ;[:li.sidebar-brand.hidden-sb-small
     ;[:a {:href "#"} "Numerix"]]
    [:li {:class (if (:show-notifications-widget @form-state) "active" "")
          :style {:margin-top "1rem"}}
     [:a.sidebar-clickable
      {:style {:color "#999"}
       :title "Notifications"
       :on-click (handler-fn
                   (rf/dispatch [:notification/toggle-notifications-widget])
                   (rf/dispatch [:common/toggle-sidebar-sm]))}
      [:i.fa.fa-bell-o.fa-fw] [:span.hidden-sb-small " Notifications"]]]

    [:li.divider]

    [:li {:class (active-if-route (site/home-route))}
     [:a {:href (site/home-route) :title "Application Overview"
          :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
         [:i.fa.fa-home.fa-fw] [:span.hidden-sb-small " Dashboard"]]]
    ;[:li {:class (active-if-route (site/projects-route))}
    ; [:a {:href (site/projects-route) :title "Projects"
    ;      :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
    ;     [:i.fa.fa-cubes.fa-fw] [:span.hidden-sb-small " Projects"]]]
    (when @(rf/subscribe [:form-data-allowed-access :contacts])
      [:li {:class (active-if-route (site/addressbook-route))}
       [:a {:href (site/addressbook-route) :title "Contacts"
            :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
           [:i.fa.fa-address-book-o.fa-fw] [:span.hidden-sb-small " Contacts"]]])
    (when @(rf/subscribe [:form-data-allowed-access :timeroll])
      [:li {:class (active-if-route (site/timeroll-route))}
       [:a {:href (site/timeroll-route) :title "Timeroll"
            :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
           [:i.fa.fa-clock-o.fa-fw] [:span.hidden-sb-small " Timeroll"]]])
    (when @(rf/subscribe [:form-data-allowed-access :invoices])
      [:li {:class (active-if-route (site/invoices-route))}
       [:a {:href (site/invoices-route) :title "Invoices"
            :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
           [:i.fa.fa-money.fa-fw] [:span.hidden-sb-small " Invoices"]]])

    [:li.divider]

    (when @(rf/subscribe [:form-data-allowed-read :meetings])
      [:li {:class (active-if-route (site/calendar-route))}
       [:a {:href (site/calendar-route) :title "Calendar"
            :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
           [:i.fa.fa-calendar.fa-fw] [:span.hidden-sb-small " Calendar"]]])

    (when @(rf/subscribe [:form-data-allowed-read :chat-room])
      [:li {:class (active-if-route (site/chat-rooms-route))}
       [:a {:href (site/chat-rooms-route) :title "Chat"
            :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
           [:i.fa.fa-comments-o.fa-fw] [:span.hidden-sb-small " Chat"]]])

    (when @(rf/subscribe [:form-data-allowed-read :meetings])
      [:li {:class (active-if-route (site/meetings-route))}
       [:a {:href (site/meetings-route) :title "Meeting Minutes"
            :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
           [:i.fa.fa-free-code-camp.fa-fw] [:span.hidden-sb-small " Meetings"]]])

    (when @(rf/subscribe [:form-data-allowed-read :documents])
      [:li {:class (active-if-route (site/documents-route))}
       [:a {:href (site/documents-route) :title "Documents"
            :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
           [:i.fa.fa-files-o.fa-fw] [:span.hidden-sb-small " Documents"]]])

    (when @(rf/subscribe [:form-data-allowed-read :knowledgebase])
      [:li {:class (active-if-route (site/knowledgebase-route))}
       [:a {:href (site/knowledgebase-route) :title "Knowledge Base"
            :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
           [:i.fa.fa-lightbulb-o.fa-fw] [:span.hidden-sb-small " Knowledge"]]])

    ;[:li.divider]
    ;[:li {:class (active-if-route (site/textblocks-route))}
    ; [:a {:href (site/textblocks-route) :title "Text Blocks"
    ;      :on-click #(rf/dispatch [:common/toggle-sidebar-sm])}
    ;     [:i.fa.fa-comments-o.fa-fw] [:span.hidden-sb-small " Text Blocks"]]]
    ]

   [:button.btn.btn-link.btn-transp.sidebar-size-toggle {:on-click #(rf/dispatch [:common/toggle-sidebar-size])}
    [:i.fa {:class (if (small-sidebar? @view-state) "fa-caret-square-o-right" "fa-caret-square-o-left")}]]])

(defn footer []
  [:footer.navbar.navbar-fixed-bottom.navbar-dark.bg-inverse.bg-sidebar.footer.text-muted.d-none.d-md-block {:role "contentinfo"}
   [:div.container-fluid
    ;[:ul.nav.navbar-nav
    ; [:li.nav-item [:a.nav-link {:href "https://github.com/twbs/bootstrap"} "About"]]
    ; [:li.nav-item [:a.nav-link {:href "https://twitter.com/getbootstrap"} "Contact"]]
    ; [:li.nav-item [:a.nav-link {:href "../getting-started/#examples"} "Imprint"]]
    ; [:li.nav-item [:a.nav-link {:href "../about/"} "About"]]]
    ;[:div.clearfix]
    [:p.text-muted "© Developed with love and Clojure by "
     [:a.footer-link {:href "http://www.agynamix.de" :target "_blank"} "AGYNAMIX."]]]])

(defn base
  "The base component for every page we render.
  It assembles the different parts like menu, sidebar, main content and footer."
  [& content]
  (let [view-state (rf/subscribe [:view-state])
        user-data (rf/subscribe [:current-user])
        form-state (rf/subscribe [:form-state])
        projects (rf/subscribe [:projects])]

       (fn []
        [:div#wrapper {:class (str (name (site/current-page)) " "
                                   (@view-state :sidebar-toggle-cls) " "
                                   (@form-state :sidebar-toggle-cls) " " (@view-state :sidebar-size-cls))}

         ;; Sidebar
         (when-not (:hide-headers-and-footers @form-state)
           [sidebar view-state form-state])

         ;; Small and wide screen navbars
         (when-not (:hide-headers-and-footers @form-state)
           [:nav#top-navigation.navbar.navbar-dark.navbar-fixed-top.navbar-expand-md.bg-sidebar
            [:button.navbar-toggler.pull-left.d-sm-none {:href "Javascript://"
                                                            :on-click #(rf/dispatch [:common/toggle-sidebar true])
                                                            :style {:background-image :none
                                                                    :color "#fff"
                                                                    }
                                                            }
             [:i.fa.fa-compass]]


            [:a.navbar-brand.d-none.d-md-block.pull-left {:href "Javascript://"
                                                          :on-click #(rf/dispatch [:common/toggle-sidebar true])
                                                          :style {:margin-left "1rem"
                                                                  :background-image :none
                                                                  :color "#fff"
                                                                  }
                                                          } "Numerix"]

            [:button.navbar-toggler.collapsed.d-sm-none.pull-right
             {:type "button"
              :on-click #(rf/dispatch [:common/toggle-navbar-collapse true])
              }

             [:span.navbar-toggler-icon]]

            [:div#navbar-collapse {
                                   :class (str "navbar-collapse collapse" (@form-state :navbar-toggle-cls))
                                   }
             [:ul.navbar-nav.mr-auto
              [:li.nav-item {:class (active-if-route (site/home-route))}
               [:a.nav-link {:href (site/home-route)} [:i.fa.fa-home] " Home "]]]

             [:ul.navbar-nav.ml-auto
              [:li.nav-item.dropdown
               [prj-sel/project-nav-selector user-data projects]]
              [:li.nav-item {:class (active-if-route (site/contact-route))}
               [:a.nav-link {:href (site/contact-route)} [:i.fa.fa-info-circle] " Contact"]]
              [:li.nav-item {:class (active-if-route (site/imprint-route))}
               [:a.nav-link {:href (site/imprint-route)} [:i.fa.fa-legal] " Imprint"]]
              [:li.nav-item.dropdown
               [user-menu user-data :right]]
              ]
             ]
         ])

         (when (:show-notifications-widget @form-state)
           [notifications/notifications-widget])

         [:div#page-content-wrapper.container-fluid

          ;; page content goes here
          [:div content]

          ;; the lower left snackbar & loader
          [snackbar-container
           [alert-box]
           [loader-spinner]]]

         ;; The footer component
         (when-not (:hide-headers-and-footers @form-state)
           [footer])

         (when-let [url (:open-document @form-state)]
           (log/info "append iframe")

           (let [f [:iframe#hiddenDownloader {
                                              :src url
                                              :style {:display "none"}
                                              }]]

             (js/setTimeout #(rf/dispatch [:common/remove-open-document-flag]) 10)
             f
             )
           #_(if-let [iframe (.getElementById js/document "hiddenDownloader")]

             (do
               (log/info "Open document 1 " url)
               (aset iframe "src" url))

             (do
               (let [iframe (.createElement js/document "iframe")]
                 (aset iframe "id" "hiddenDownloader")
                 (aset iframe "src" url)
                 (aset iframe "style" "display" "none")
                 (h/tty-log "Open document 2 " url iframe)

                 (.appendChild js/document.body iframe)
                 ))))

         ])


      ))



