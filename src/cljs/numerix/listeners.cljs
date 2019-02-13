(ns numerix.listeners
  (:require [numerix.state :as s]
            [numerix.lib.helpers :as h]
            [re-frame.db :as db]
            [taoensso.timbre :as log]
            [numerix.views.common-controls :as ctrl]
            [re-frame.core :as rf]))

;(def sidebar (.get (js/$ "#sidebar-wrapper") 0)) doesn't work if cached on init
;(def top-nav (.get (js/$ "#top-navigation") 0))

(defn contained? [parent event]
  (try
    (.contains js/$ parent (.-srcElement event))
    (catch :default e
      false)))

(defn set-current-screen-size []
  (rf/dispatch [:set-current-screen-size (.-innerWidth js/window) (.-innerHeight js/window)]))

(defn on-window-resize [ evt ]
  (set-current-screen-size))

(defn wrapper-click-default-fn [form-state]
  (rf/dispatch [:wrapper-click-update not]))

(defn outside-click-default-fn [form-state]
    (when-not
      (or (:edit-item form-state)
          (not (:show-details form-state)))
      (rf/dispatch [:show-detail-area false])))

(defn on-wrapper-click [ evt ]
  (let [
        sidebar (.get (js/$ "#sidebar-wrapper") 0)
        top-nav (.get (js/$ "#top-navigation") 0)
        form-state (rf/subscribe [:form-state])
        ignore-outside-click (rf/subscribe [:form-state-field :mouse-inside-detail-area-no-follow])

        outside-click-fn (:on-outside-click @form-state outside-click-default-fn)
        wrapper-click-fn (:on-wrapper-click @form-state wrapper-click-default-fn)]

    ;(log/info "on wrapper click " (:mouse-inside-detail-area @form-state) @ignore-outside-click)
    ;; FIXME: When editing a form, cancel edit and then outside click without moving the mouse over the
    ;; form again, the form detail view is not closed

    (when-not (or (contained? sidebar evt) (contained? top-nav evt))
      (wrapper-click-fn @form-state)
      (when-not (or
                  (:mouse-inside-detail-area @form-state)
                  @ignore-outside-click)
        (outside-click-fn @form-state)))))


(defn init-listeners []
  (set-current-screen-size)
  (.addEventListener js/window "resize" on-window-resize)
  (.addEventListener js/window "click" on-wrapper-click))

