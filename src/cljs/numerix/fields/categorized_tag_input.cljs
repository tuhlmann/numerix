;; This is backup of the tag-input field
(ns numerix.fields.categorized-tag-input
  (:require [reagent.core :as r]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]))

;; Keyboard keys

(def keycodes {:TAB 9
               :ENTER 13
               :BACKSPACE 8
               :LEFT 37
               :UP 38
               :RIGHT 39
               :DOWN 40
               :COMMA 188})

(defn category-item-id
  ([item]
   (category-item-id item nil))
  ([item default]
   (if item
     (if (map? item)
       (:_id item)
       item)

     default)))

(defn category-item-label
  ([item]
   (category-item-label item nil))
  ([item default]
   (if item
     (if (map? item)
       (:label item)
       item)

     default)))

(defn compare-tag-item [tag item]
  (= (category-item-id item) tag))


(defn on-add [props new-tag]
  (let [{:keys [panel-opened transform-tag-fn tags input-value on-change]} props
        {:keys [category item]} new-tag
        item (transform-tag-fn category item)
        new-tags (into [] (concat @tags [item]))]

    (reset! tags new-tags)
    (reset! input-value "")
    (reset! panel-opened false)

    (when (fn? on-change)
      (on-change new-tags))))

(defn add-text-as-tag [{:keys [tag-id category-id] :as props}]
  (on-add props { :category category-id :item tag-id }))

(defn matching-items [props items]
  (let [{:keys [tags input-value]} props]
    (->>
      (filterv #(cue/includes? (cue/lower (category-item-label %)) (cue/lower @input-value)) items)
      (filterv (fn [i] (not-any? #(= (cue/lower (category-item-id %)) (cue/lower (category-item-id i))) @tags))  ))))

(defn full-match-in-items [input-value & item-lists]
  (some (fn [items]
          (some #(= @input-value (category-item-label %)) items)) item-lists))

(defn add-selected-tag [props]
  (let [{:keys [add-new tags input-value panel-opened categories selection]} props
        full-match (full-match-in-items input-value @tags)]
    (when-not (or
                (and
                  (not @panel-opened)
                  (> (count @input-value) 0))
                full-match)

      (let [category (get categories (:category @selection 0))
            items (:items category)
            matching-items (if @panel-opened (matching-items props items) nil)
            item (if add-new (category-item-id (get matching-items (:item @selection)) @input-value)
                             (category-item-id (get matching-items (:item @selection))))]

        (when-not (or (nil? item) (< (count (str/trim item)) 1))
          (on-add props {:category (:id category)
                         :item item}))))))


(defn tag-content [{:keys [input-value text] :as props}]

  (let [label (category-item-label text)
        text-lower (cue/trim (cue/lower label))
        input-lower (cue/trim (cue/lower @input-value))
        start-idx (.indexOf text-lower input-lower)
        end-idx (+ start-idx (count @input-value))]

    [:span
     (when (> start-idx 0)
       [:span.cti__tag__content--regular {:key 1} (subs label 0 start-idx)])

     [:span {:key 2
             :class (if (> start-idx -1) "cti__tag__content--match" "cti__tag__content--regular")}
      (subs label start-idx end-idx)]

     (when (< end-idx (count label))
       [:span.cti__tag__content--regular {:key 3} (subs label end-idx)])]))


(defn tag [{:keys [key on-delete selected addable deletable style] :as props}]

  (let [cls (str "cti__tag" (if selected " cti-selected" ""))]

    [:div {:key key
           :class cls
           :on-click (fn [e]
                       (.preventDefault e)
                       (when addable
                         (add-text-as-tag props)))
           :style (:base style {})
           }
     [:div.cti__tag__content {:style (:content style {})}
      [tag-content props]

      (when deletable
        [:span.cti__tag__delete {:on-click on-delete
                                 :style (:delete style {})}
         (h/unescape "&times;")]
        ;[:span.cti__tag__delete.fa.fa-close {:on-click on-delete
        ;                         :style (:delete style {})}]


        )]]))

(defn add-button [{:keys [category-id type title input-value create-new-text-fn add-new single items full-match btn-selected?] :as props}]

  (enc/when-lets [my-title (or type title)
                  _ (and add-new (not full-match) (not single))]

                 (when (> (count items) 0)
                   [:span.cti__category__or "or"])

                 [:button.cti__category__add-item {:type "button"
                                                   :class (if btn-selected? " cti-selected" "")
                                                   :on-click (fn [e]
                                                               (on-add props {:category category-id
                                                                              :item @input-value
                                                                              }))}
                  (create-new-text-fn title @input-value)]))


(defn item-to-tag [props idx item]
  (let [{:keys [input-value selected selected-item single tag-style-fn]} props
        selected? (and selected (or (= idx selected-item) single))]

    [tag (merge props {
                       :key (str (category-item-id item) "_" idx)
                       :selected selected?
                       :input-value input-value
                       :tag-id (category-item-id item)
                       :text (category-item-label item)
                       :addable true
                       :deletable false
                       :style (tag-style-fn item)
                       })]))


(defn category [{:keys [hide-single-category categories tags items title selected selected-item input-value] :as props}]

  (let [matching-items (matching-items props items)
        full-match     (boolean (full-match-in-items input-value items @tags))
        btn-selected?  (and (or (= (count matching-items) 0) (>= selected-item (count matching-items))) selected)
        plain-list? (and hide-single-category (= (count categories) 1))]

    [:div.cti__category
     (when-not (or plain-list? (empty? matching-items))
       [:h5.cti__category__title title])
     [:div.cti__category__tags
      (map-indexed (fn [idx item]
                     (item-to-tag props idx item)) matching-items)

      (when-not plain-list?
        [add-button (merge props {
                                  :full-match full-match
                                  :btn-selected? btn-selected?
                                  })])]]))


(defn handle-backspace [props e]
  (let [{:keys [input-value tags on-tag-deleted]} props]
    (when (= (count (cue/trim @input-value)) 0)
      (.preventDefault e)
      (on-tag-deleted (- (count @tags) 1)))))


(defn handle-arrow-left [props]
  (let [{:keys [selection]} props
        result (- (:item @selection 0) 1)]

    (reset! selection {:category (:category @selection)
                       :item (if (>= result 0) result 0)})))


(defn handle-arrow-up [props]
  (let [{:keys [selection]} props
        result (- (:item @selection 0) 1)]

    (reset! selection {:category (if (>= result 0) result 0)
                       :item 0})))

(defn handle-arrow-right [props]
  (let [{:keys [selection categories]} props
        result (+ 1 (:item @selection 0))
        cat (get categories (:category @selection 0))
        items-count (count (:items cat))]

    (reset! selection {:category (:category @selection)
                       :item (if (<= result items-count) result items-count )})))

(defn handle-arrow-down [props]
  (let [{:keys [categories selection]} props
        result (+ 1 (:category @selection 0))
        cat-len (count categories)]

    (reset! selection {:category (if (< result cat-len) result (dec cat-len))
                       :item 0})))

(defn on-key-down [props e]

  (let [{:keys [input-value]} props]
    (condp = (.-keyCode e)
      (:ENTER keycodes)
      (add-selected-tag props)

      (:TAB keycodes)
      (add-selected-tag props)

      (:COMMA keycodes)
      (do
        (add-selected-tag props))

      (:BACKSPACE keycodes)
      (handle-backspace props e)

      (:LEFT keycodes)
      (handle-arrow-left props)

      (:UP keycodes)
      (handle-arrow-up props)

      (:RIGHT keycodes)
      (handle-arrow-right props)

      (:DOWN keycodes)
      (handle-arrow-down props)

      ;(log/info "k " (.-keyCode e))
      :default
      )))

(defn tag-input-area [props]

  (fn [{:keys [input-value min-chars categories tags panel-opened tag-style-fn placeholder] :as props }]

    (let [size (if (= (count @input-value) 0) (count placeholder) (count @input-value))
          all-cat-entries (into [] (mapcat :items categories))]

      [:div.cti__input
       (map-indexed (fn [idx t]
                      (let [e (first (filterv (partial compare-tag-item t) all-cat-entries))]
                        ^{:key (str (cue/snake (category-item-id t)) "_" idx)}
                        [tag (merge props {
                                           :selected false
                                           :tag-id (category-item-id e t)
                                           :text (category-item-label e t)
                                           :addable false
                                           :deletable true
                                           :on-delete (fn []
                                                        (swap! tags h/vec-remove idx))
                                           :style (tag-style-fn t)})]
                        )) @tags)
       [:input.cti__input__input
        {:type "text"
         :value @input-value
         :size (+ size 2)
         :on-focus (handler-fn
                     (reset! panel-opened (and
                                            (> (count all-cat-entries) 0)
                                            (boolean (>= (count @input-value) min-chars)))))
         :on-change (handler-fn
                      (reset! input-value (.-target.value event))
                      (reset! panel-opened (boolean (>= (count @input-value) min-chars))))
         :on-key-down (partial on-key-down props)
         :placeholder placeholder
         :aria-label placeholder}]
       [:div.cti__input__arrow {:class (if @panel-opened "up" "down")
                                :on-click (handler-fn (reset! panel-opened (not @panel-opened)))}]]

      )

    ))


(defn tag-panel [props]

  (fn [{:keys [categories selection] :as props}]

    [:div.cti__panel
     (doall (map-indexed (fn [idx c]
                           ^{:key idx}
                           [category (merge props {
                                                   :items (:items c)
                                                   :category-id (:id c)
                                                   :title (:title c)
                                                   :selected (= (:category @selection) idx)
                                                   :selected-item (:item @selection)
                                                   :type (:type c)
                                                   :single (:single c)})]
                           ) categories))]))



(defn categorized-tag-input
  "Categorized tagged input component
   - add-new (boolean, true): If true, allows the user to create new tags that are not set in the dataset
   - hide-single-category (boolean, true): If true and there is only one category it is hidden and shown as a plain list
   - min-chars (Int, 1): The number of characters before the selection panel opens
   - categories ([] or {}, req):
   - transform-tag-fn (fn): function that will receive the category id and the selected item and must return a string.
                            This string will be the resultant string.
                            Useful if you need to apply a transformation to the tags.
                            item is resulting tag
   - tags ([] of string, []): Array with the initial tags
   - on-blur (fn, noop): callback for when the input loses focus
   - on-change (fn, noop): callback for when the input changes. It does not get an event as parameter,
                     it gets the array of tags after the change.
   - placeholder (string, 'Add a tag'): A placeholder will be given in the input box.
   - tag-style-fn (fn): A function from the tag text (string) to an object with any or all of the following keys:
       - base, content and delete.
       The values are maps. This example renders 1-letter long tags in red:
       text => text.length === 1 ? {base: {color: \"red\"}} : {}\t() => ({})\n
   - create-new-text-fn (fn, [title, text] => 'Create new ${title} ${text}') A function that returns the text to display when the user types an unrecognized tag,
       given a title and text.
  "
  [& args]
  (let [panel-opened (r/atom false)
        input-value (r/atom "")
        selection (r/atom {:category 0 :item 0})
        mouse-inside (r/atom false)]
    (fn [& {:keys [add-new
                   hide-single-category
                   min-chars
                   categories
                   transform-tag-fn
                   tags
                   on-blur
                   on-change
                   placeholder
                   tag-style-fn
                   create-new-text-fn]
            :or {add-new true
                 hide-single-category true
                 min-chars 1
                 transform-tag-fn (fn [cat-id item] item)
                 on-blur (fn [_] (fn [] (reset! panel-opened false)))
                 on-change (handler-fn)
                 placeholder "Add a tag"
                 tag-style-fn (fn [_] {})
                 create-new-text-fn (fn [title, text] (str "Create new " title " " text)) }}]

      (let [props {
                   :selection selection
                   :panel-opened panel-opened
                   :add-new add-new
                   :hide-single-category hide-single-category
                   :min-chars min-chars
                   :categories categories
                   :transform-tag-fn transform-tag-fn
                   :tags tags
                   :input-value input-value
                   :on-blur on-blur
                   :on-change on-change
                   :on-tag-deleted #(swap! tags h/vec-remove %)
                   :placeholder placeholder
                   :tag-style-fn tag-style-fn
                   :create-new-text-fn create-new-text-fn
                   }]

        [:div.cti__root {
                         :on-mouse-enter (handler-fn (reset! mouse-inside true))
                         :on-mouse-leave (handler-fn (reset! mouse-inside false))
                         :on-blur (fn [_]
                                    (when-not @mouse-inside
                                      (r/next-tick #(reset! panel-opened false))))

                         }
         [tag-input-area props]

         (when @panel-opened
           [tag-panel props])]))))

(defn tag-list [& {:keys [tags]}]
  [:p.form-control-static
   [:i.fa.fa-tags] " "
   (str/join ", " tags)
   #_(map-indexed (fn [idx tag]
                    ^{:key idx}
                    [:span {:style {:margin-right "0.6rem"}} tag]) tags)])

(defn tag-list-mini [tags]
  (when-not (empty? tags)
    [:span
     [:i.fa.fa-tags] " " (str/join ", " tags)]))

