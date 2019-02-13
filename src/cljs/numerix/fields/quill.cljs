(ns numerix.fields.quill
  (:require [reagent.core         :as r]
            [reagent.dom.server   :as r-svr]
            [reagent.interop      :refer-macros [$ $!]]
            [taoensso.timbre      :as log]
            [clojure.string       :as str]
            [numerix.lib.helpers  :as h]
            [taoensso.encore      :as enc]
            [re-com.core          :refer-macros [handler-fn]]
            [re-frame.core        :as rf]))

(def default-colors (map (fn [c] {:value c})
  ["rgb(0, 0, 0) " "rgb(230, 0, 0) " "rgb(255, 153, 0) "
   "rgb(255, 255, 0) " "rgb(0, 138, 0) " "rgb(0, 102, 204) "
   "rgb(153, 51, 255) " "rgb(255, 255, 255) " "rgb(250, 204, 204) "
   "rgb(255, 235, 204) " "rgb(255, 255, 204) " "rgb(204, 232, 204) "
   "rgb(204, 224, 245) " "rgb(235, 214, 255) " "rgb(187, 187, 187) "
   "rgb(240, 102, 102) " "rgb(255, 194, 102) " "rgb(255, 255, 102) "
   "rgb(102, 185, 102) " "rgb(102, 163, 224) " "rgb(194, 133, 255) "
   "rgb(136, 136, 136) " "rgb(161, 0, 0) " "rgb(178, 107, 0) "
   "rgb(178, 178, 0) " "rgb(0, 97, 0) " "rgb(0, 71, 178) "
   "rgb(107, 36, 178) " "rgb(68, 68, 68) " "rgb(92, 0, 0) "
   "rgb(102, 61, 0) " "rgb(102, 102, 0) " "rgb(0, 55, 0) "
   "rgb(0, 41, 102) " "rgb(61, 20, 10) "]))

(def default-toolbar-items2
  [
   { :label "Formats" :type :group :items
            [
             { :label "Font" :type :font :items  [
                                                   { :label "Heading"    :value "1" }
                                                   { :label "Subheading" :value "2" }
                                                   { :label "Normal"     :value "" :selected true }
                                                   ]
              }
             { :label "Size" :type :size :items  [
                                                   { :label "Small"  :value "10px" }
                                                   { :label "Normal" :value "13px" :selected true }
                                                   { :label "Large"  :value "18px" }
                                                   { :label "Huge"   :value "32px" }
                                                   ]}
             { :label "Alignment" :type :align :items  [
                                                         { :label "" :value "left" :selected true }
                                                         { :label "" :value "center" }
                                                         { :label "" :value "right" }
                                                         { :label "" :value "justify" }
                                                         ]}
             ]}
   { :label "Text" :type :group :items  [
                                          { :type :bold :label "Bold" }
                                          { :type :italic :label "Italic" }
                                          { :type :strike :label "Strike" }
                                          { :type :underline :label "Underline" }
                                          { :type :separator }
                                          { :type :color :label "Color" :items default-colors }
                                          { :type :background :label "Background color" :items default-colors }
                                          { :type :separator }
                                          { :type :link :label "Link" }
                                          ]}

   { :label "Blocks" :type :group :items  [
                                            { :type :bullet :label "Bullet" }
                                            { :type :separator }
                                            { :type :list :label "List" }
                                            ]}

   { :label "Blocks" :type :group :items  [
                                            { :type :image :label "Image" }
                                            ]}
   ])

(def default-toolbar-items
  [{:type :group :items
          [{:type :select :cls :ql-header :items [{ :label "Heading 1" :value "1" }
                                                  { :label "Heading 2" :value "2" }
                                                  { :label "Heading 3" :value "3" }
                                                  { :label "Normal"    :value "" :selected true }]}

           {:type :select :cls :ql-font :items [{ :label "Sans Serif" :value "" :selected true }
                                               { :label "Serif"      :value "serif" }
                                               { :label "Monospace"  :value "monospace"}]}
           ]}
   {:type :group :items
          [{ :type :button :cls :ql-bold      :label "Bold"}
           { :type :button :cls :ql-italic    :label "Italic" }
           { :type :button :cls :ql-underline :label "Underline" }
           ]}
   {:type :group :items
          [{:type :select :cls :ql-color }
           {:type :select :cls :ql-background }]}

   {:type :group :items
          [{:type :button :cls :ql-list :value :ordered :label "Numbered List"}
           {:type :button :cls :ql-list :value :bullet :label "Bullet List"}
           {:type :select :cls :ql-align :items [{ :label "Left"    :value "" :selected true }
                                                 { :label "Center"  :value "center" }
                                                 { :label "Right"   :value "right" }
                                                 { :label "Justify" :value "justify" }
                                                 ]}
           ]}

   {:type :group :items
          [{ :type :button :cls :ql-link       :label "Link"}
           { :type :button :cls :ql-image      :label "Image" }
           { :type :button :cls :ql-blockquote :label "Quote" }
           { :type :button :cls :ql-code-block :label "Code Block" }
           ]}

   {:type :group :items
          [{ :type :button :cls :ql-clean      :label "Remove Formatting"}]}

   ]
  )

(def dirty-props ["id" "class" "modules" "toolbar" "formats" "styles" "theme" "pollInterval"])


(declare render-item)
(defn render-group [item key]
  [:span {
          :key key
          :class "ql-formats" }

   (map-indexed render-item (:items item))])

(defn render-choice-item [item key]
  [:option {
            :key key
            :value (:value item) }
   (:label item)])


(defn render-choices [item key]
  (let [attrs {:key key
               :class (str (name (:cls item)))
               :default-value (:value (first (filter :selected (:items item))))}

        choice-items (map-indexed (fn [key item]
                                    (render-choice-item item key)) (:items item))]

  [:select attrs choice-items]))

(defn render-action [item key]
  [:button {:key key
            :class (str (name (:cls item)))
            :value (:value item)
            :title (:label item)}])


(defn render-item [key item]
  (condp some [(:type item)]
         #{:group}
         (render-group item key)

         #{:select }
         (render-choices item key)

         (render-action item key)))





(defn quill-toolbar [& {:keys [toolbar-id class items]
                            :or {
                                 toolbar-id (h/short-rand)
                                 class      "toolbar-container"
                                 items      default-toolbar-items
                                 }}]
  (let [children (map-indexed render-item items)
        toolbar-html (->
                       (map r-svr/render-to-static-markup children)
                       (str/join))]
    (fn []
      [:div {:id toolbar-id
             :class (str "quill-toolbar " class)
             :dangerouslySetInnerHTML
             {:__html toolbar-html}}])))

;;; END Toolbar




(defn set-editor-contents [editor value]
  (try
    (let [sel (.getSelection editor)
          v (if-not (nil? value) value "")]
      (.pasteHTML editor v)
      (when sel
        (.setSelection editor sel)))
    (catch :default e
      ;(log/error "Error in setSelection" e)
      )))

(defn set-editor-selection [editor range]
  (when range
    (let [length (.getLength editor)
          start (js/Math.max 0 (js/Math.min (.-start range), (dec length)))
          end (js/Math.max (.-start range) js/Math.min (.-end range) (dec length))]

      ($! range :start start)
      ($! range :end end)))
    (.setSelection editor range))

(defn set-editor-read-only [editor read-only]
  (if read-only
    ($ editor editor.disable)
    ($ editor editor.enable)))

(defn set-custom-formats [editor formats]
  (when formats
    (log/warn "setting custom formats is not suported yet")))

(defn get-html [editor-id]
  (.html (js/$ (str "#" editor-id " .ql-editor"))))

(defn quill
  "- value: an atom or simple value that holds the value to render in the editor. Simple values work only
            if editor is in read-only state
   - edit-source-id: an optional atom passed in that's set by the editor that changes the value atom.
            If multiple read-write editors should display the same source this is used
            to determine if we need to re-render or not. We do not rerender on our own changes.
  "
  [& {:keys [value edit-source-id editor-id style class on-key-press on-key-down on-key-up toolbar
             modules formats on-editor-change on-editor-selection-change
             theme styles poll-intervall read-only]
      :or {editor-id   (h/short-rand)
           style       {}
           class       "wysiwyg-editor"
           theme       "snow"
           ;modules     {:link-tooltip true
           ;             :image-tooltip true}
           modules     {}
           toolbar     default-toolbar-items
           on-key-press (handler-fn)
           on-key-down  (handler-fn)
           on-key-up    (handler-fn)
           }}]

  (let [editor-atom (atom nil)
        toolbar? (r/atom false)
        toolbar-id (h/short-rand)
        div-cls (str "quill " class)]
    (fn []
      (r/create-class
        {
         :component-did-mount
         (fn [this]
           ;(log/info "component did mount")
           (let [pi (if poll-intervall {:pollIntervall poll-intervall} {})
                 ro (if read-only {:readOnly read-only} {})
                 s (if styles {:styles styles} {})
                 config (merge pi ro s {
                                        :placeholder (if-not read-only "Write a note..." "")
                                        :theme theme
                                        :modules (merge modules (if @toolbar? {:toolbar (str "#" toolbar-id)} {}))
                                        })
                 quill (js/Quill. (str "#" editor-id) (clj->js config))]

             ;(when @toolbar?
             ;  (.addModule quill "toolbar" (clj->js {:container (str "#" toolbar-id)})))

             (set-custom-formats quill formats)
             (set-editor-contents quill (h/ref-or-val value))

             (.on quill "text-change" (fn [delta, old-delta, source]
                                        (when (fn? on-editor-change)
                                          (on-editor-change quill (get-html editor-id) delta source))
                                        ;(when (= source "user")
                                        ;  ;(h/tty-log "Contents " (.getContents quill))
                                        ;  (when (satisfies? IDeref value)
                                        ;    (reset! value (get-html editor-id))
                                        ;    (when (satisfies? IDeref edit-source-id)
                                        ;      (reset! edit-source-id editor-id))
                                        ;    )
                                        ;  )
                                        ))

             (.on quill "selection-change" (fn [range old-range source]
                                             (when (fn? on-editor-selection-change)
                                               (on-editor-selection-change quill range source))))

             (reset! editor-atom quill)
             ;(h/tty-log "component did mount " node (.' node -id))
             ))

         :component-will-update
         (fn [this]
           (let [changer-id (if (satisfies? IDeref edit-source-id) @edit-source-id editor-id)]
             (when (and
                     @editor-atom
                     (or read-only
                         (not= editor-id changer-id)))
               (set-editor-contents @editor-atom (h/ref-or-val value)))))


         :component-will-unmount
         (fn [this]
           (when @editor-atom
             ;(.destroy @editor-atom) no longer needed on Quill 1
             ))

         :should-component-update
         (fn [this next-props next-state]
           ;; Check if one of the changes should trigger a re-render.
           ;for (var i=0; i<this.dirtyProps.length; i++) {
           ;  var prop = this.dirtyProps[i];
           ;  if (nextProps[prop] !== this.props[prop]) {
           ;    return true;
           ;  }
           ;}
           ;(log/warn "Checking dirty props for editor update not supported yet.")

           false)

         :reagent-render
         (fn []
           (h/ref-or-val value)
           ;(log/info ":reagent-render is Atom " (instance? IDeref value))
           (reset! toolbar? (and toolbar (not read-only)))
           [:div {:id          (str "container-" editor-id)
                  :style       style
                  :class       div-cls
                  :on-key-press on-key-press
                  :on-key-down on-key-down
                  :on-key-up   on-key-up
                  :on-change   (fn [e]
                                 (.preventDefault e)
                                 (.stopPropagation e))
                  }

             ;; Quill modifies these elements in-place,
             ;; so we need to re-render them every time.
             ;; Render the toolbar unless explicitly disabled.
            (when @toolbar?
              [quill-toolbar :toolbar-id toolbar-id :items toolbar])

            [:div.editor-container
             {:id editor-id
              :key (str "editor-" editor-id)
              :class "quill-contents"}]
            ])

         }))))

(defn as-html [text]
  (fn []
    [quill :read-only true :value text :class "wysiwyg-editor readonly"]))


