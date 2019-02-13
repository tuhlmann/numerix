(ns numerix.fields.autoscroll
  (:import goog.fx.dom.Scroll)
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [numerix.fields.markdown-render :as markdown]
            [numerix.site :as site]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]
            [numerix.api.project :as prj-api]
            [re-frame.core :as rf]
            [reagent.core :as reagent]))


;; Auto-scrolling ==============================================================
;; See https://gist.github.com/martinklepsch/9565a5ea099c44bc2931

(defn scroll! [el start end time]
  (.play (goog.fx.dom.Scroll. el (clj->js start) (clj->js end) time)))

(defn scrolled-to-end? [el tolerance]
  ;; at-end?: element.scrollHeight - element.scrollTop === element.clientHeight
  (> tolerance (- (.-scrollHeight el) (.-scrollTop el) (.-clientHeight el))))

(defn scrolled-to-start? [el tolerance]
  (> tolerance (.-scrollTop el)))

(defn handle-scroll [was-outside-start-area? on-scroll-start e]
  (if (and
        on-scroll-start
        (scrolled-to-start? (.-target e) 100))

    (when @was-outside-start-area?
      (reset! was-outside-start-area? false)
      (on-scroll-start e))

    (when-not @was-outside-start-area?
      (reset! was-outside-start-area? true))))

(defn autoscroll-list [{:keys [class scroll?] :as opts}]
  (let [should-scroll (r/atom true)
        was-outside-start-area? (atom true)]

    (r/create-class
      {:display-name "autoscroll-list"

       :component-did-mount
                     (fn [this]
                       (let [n (r/dom-node this)]
                         (scroll! n [0 (.-scrollTop n)] [0 (.-scrollHeight n)] 0)))

       :component-will-update
                     (fn [this]
                       (let [n (r/dom-node this)]
                         ;;(log/info "scroll top? " (.-scrollTop n))
                         ;; (pp/pprint {:scrollheight (.-scrollHeight n)
                         ;;             :scrolltop    (.-scrollTop n)
                         ;;             :clientHeight (.-clientHeight n)
                         ;;             :to-scroll    (- (.-scrollHeight n) (.-scrollTop n))
                         ;;             :scrolled     [(- (.-scrollHeight n) (.-scrollTop n)) (.-clientHeight n)]})
                         (reset! should-scroll (scrolled-to-end? n 100))))

       :component-did-update
                     (fn [this]
                       (let [scroll? (:scroll? (r/props this))
                             n       (r/dom-node this)]
                         (when (and scroll? @should-scroll)
                           (scroll! n [0 (.-scrollTop n)] [0 (.-scrollHeight n)] 300))))

       :render
       ;; When getting next and prev props here it would be possible to detect if children have changed
       ;; and to disable scrollbars for the duration of the scroll animation
         (fn [this]
           (let [children (r/children this)
                 {:keys [on-scroll-start]} (r/props this)]
             (into [:div
                    {:class class
                     :on-scroll (partial
                                  handle-scroll
                                  was-outside-start-area?
                                  on-scroll-start)}] children)))})))