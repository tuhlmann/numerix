(ns numerix.views.fields
  (:require [reagent.core :as r]
            [re-com.core :refer-macros [handler-fn]]
            [secretary.core :refer [dispatch!]]
            [taoensso.encore :as enc]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [cuerdas.core :as cue]
            [numerix.state :as s]
            [numerix.fields.quill :as quill]
            [numerix.fields.file-upload :as file-upload]
            [validateur.validation :as v]
            [numerix.lib.datatypes :as d]
            [numerix.lib.helpers :as h]
            [numerix.fields.tag-input :as tag]
            [numerix.fields.field-type-config :as ftc]
            [re-frame.core :as rf]
            [reagent.ratom :as ratom]))

(defn as-vec
  [arg]
  (if (sequential? arg)
    (vec arg)
    (vec [arg])))

(def counter-pattern #".*\{counter(:\d+)?}.*")

(defn validate-form-all [form-config form-data]
  (let [validate-fn (:validate-fn form-config)
        validation (:validation form-config)]
    (cond
      (fn? validate-fn)
      (let [vali-res ((validate-fn form-data) form-data)]
        (rf/dispatch [:reset-form-data-error vali-res])
        vali-res)

      :else
      (when validation
        (let [vali-res (validation form-data)]
          (rf/dispatch [:reset-form-data-error vali-res])
          vali-res)))))

(defn validate-form-path [form-config form-data vali-path]
  (let [validate-fn (:validate-fn form-config)
        validation (:validation form-config)]
    (cond
      (fn? validate-fn)
      (let [vali-res ((validate-fn form-data) form-data)]
        (rf/dispatch [:update-form-data-error vali-res vali-path])
        vali-res)

      :else
      (when validation
        (let [vali-res (validation form-data)]
          (rf/dispatch [:update-form-data-error vali-res vali-path])
          vali-res)))))

;;; SELECT field

(defn select-field-list [{:keys [key-fn label value entries on-change]}]
  (log/info "selected is " (pr-str value))
  (log/info "All options " (pr-str entries))
  [:select.form-control.c-select {:value (key-fn @value)
                         :on-change on-change}
   (for [item entries]
     ^{:key (key-fn item)} [:option {:value (key-fn item)} (get-in item label)])
   ])

(defn select-field
  ([prompt value options] (select-field prompt value options :no-such-field))
  ([prompt value options validate-key]
   (let [form-state (rf/subscribe [:form-state])
         field-errors (s/validation-errors @form-state validate-key)
         has-error (not (empty? field-errors))
         ;key-seq [:_id :$oid]
         key-fn #(.toString (:_id %))
         ]
     [:div.form-group.row {:class (if has-error "has-error" "")}
      [:label.col-3.form-control-label prompt]
      [:div.col-9
       [select-field-list {:key-fn key-fn
                           :value value
                           :label [:name]
                           :entries options
                           :on-change (fn [ev]
                                        (enc/when-lets [target-id (.-target.value ev)
                                                        new-value (first (filter #(= (key-fn %) target-id) options))]

                                                       (log/info "reset value to " (pr-str new-value))
                                                       (reset! value new-value)))}]

       ;[:input.form-control {:value @value
       ;                      :class (if has-error "form-control-error" "")
       ;                      :type "text"
       ;                      :on-change #(reset! value (.-target.value %))}]

       (if has-error
         [:ul.text-muted.list-unstyled.m-b-0
          (for [err field-errors]
            ^{:key err}
            [:li [:small err]])]
         nil)]])))


;;; END SELECT field

(defn pfield
  "A new path field working with re-frame semantics
   A value is either set as a path or a combination of subscription / dispatch"

  [& {:keys [path subscription dispatch-fn type vali-set vali-path nested-field] :as options
      :or {type :text
           vali-path path
           nested-field false}}]

  (let [field-id (h/short-rand)
        form-config (rf/subscribe [:form-config])
        form-data (rf/subscribe [:form-data])
        value-subs (rf/subscribe [:form-data-field path])
        field-type-config (ftc/field-type-config
                            type
                            options
                            {:value value-subs
                             :on-change (fn [new-value]
                                          (if path
                                            (rf/dispatch [:form-data-field-change path new-value])
                                            (dispatch-fn new-value)))
                             })
        all-vali-sets (remove nil? [vali-set (:vali-set field-type-config)])
        all-vali-sets (if (seq all-vali-sets)
                        (reduce v/compose-sets all-vali-sets)
                        nil)]

    (fn [& {:keys [path label help field-width vali-set vali] :as options
            :or {field-width 9
                 }}]

      (let [vali-res (validate-form-path @form-config @form-data vali-path)
            vali-path (if vali vali (if path path :no-such-field))
            global-vali-err (v/errors vali-path (or vali-res {}))
            local-vali-err (when (fn? all-vali-sets)
                             (let [v (all-vali-sets {:value @value-subs :shadow-value ((:display-value field-type-config))})
                                   err (into (v/errors :value v) (v/errors :shadow-value v))
                                   field-key (keyword (str "value_" field-id))]
                               (rf/dispatch [:update-form-data-error {field-key (into #{} err)} field-key])
                               err))

            field-errors (concat local-vali-err global-vali-err)
            has-error (not (empty? field-errors))
            has-label (some? label)
            inline-lbl? (and nested-field has-label)
            error-cls (if has-error "has-error has-danger" "")
            row-cls (if has-label "row" "")
            field-width-cls (if (or has-label nested-field)
                              (str "col-12 col-sm-" field-width)
                              "")
            props (r/merge-props options {:class (if has-error "form-control-error" "")})
            ]

        [:div
         {:class (if nested-field
                   (str field-width-cls " " error-cls)
                   (str row-cls " form-group " error-cls))}
         (when has-label
           [:label {:class (if-not nested-field "col-12 col-sm-3 form-control-label"
                                                (if inline-lbl? " form-control-label-inline" " form-control-label"))} label])
         [:div {:class (if-not nested-field field-width-cls (if inline-lbl? "display-inline" ""))}

          ((:component field-type-config) props)

          (if has-error
            [:ul.text-muted.list-unstyled.m-b-0
             (map-indexed (fn [key err]
                            ^{:key key}
                            [:li [:small err]]
                            ) field-errors)]
            nil)
          (if help
            [:div.text-muted.text-small help])
          ]]
        ))))

(defn pfields
  "pfields puts multiple pfield instances into one form line"
  [& {:keys [label fields label-cls] :as options
      :or {label-cls "col-12 col-sm-3"}}]

  (let [has-line-label (some? label)
        has-field-label (some? (map :label fields))]

    (fn [& {:keys [label fields] :as options}]

      [:div.form-group.row
       (when has-line-label
         [:label.form-control-label {:class label-cls} label])

       (for [field fields]
         (do
           ^{:key (:path field)} [pfield
                                  :path (:path field)
                                  :field-width (:field-width field)
                                  :type (:type field)
                                  :label (:label field)
                                  :help (:help field)
                                  :vali-set (:vali-set field)
                                  :vali (:vali field)
                                  :vali-path (:vali-path field)
                                  :nested-field true]
           ;^{:key (:path field)} [mapply pfield (assoc field :nested-field true)]

           )

         )
       ]
      )

    )
  )



;;; validate against validateur sets pass messages into field-errors
;;; FIXME field does not work if form-data is a map not a cursor or ratom
;;; TODO: rewrite field in order to use subscriptions and events instead of modifiying the value itself
;(defn field
;  "Wrapper for an input field
;  - :parse-type or :to-val and :from-val
;  "
;  [& {:keys [path value shadow-value type from-val to-val vali-set] :as options}]
;  (let [{:keys [form-data]} (s/form-config-map)
;        field-id (h/short-rand)
;        parse-type (or (:type options) (:parse-type options) :string)
;        value-a (if path (r/cursor form-data (as-vec path)) nil)
;        ; if number field we create a string shadow field that shows the value
;        to-val (if (= type :checkbox)
;                 d/str-to-bool
;                 (or to-val #(ftc/parse-by-type parse-type %)))
;
;        from-val (if (= type :checkbox)
;                   d/bool-to-str
;                   (or from-val #(str %)))
;        gen-shadow-field (or (and to-val from-val)
;                             (and parse-type (not= parse-type :string) (not value)))
;        has-shadow-field (or gen-shadow-field shadow-value)
;        shadow-a (if gen-shadow-field (r/atom (from-val (if value-a @value-a @value))) nil)]
;    (fn [& {:keys [value shadow-value vali vali-map label type help field-width] :as options
;        :or {type :text
;             field-width 9
;             }}]
;      (let [vali-path (if vali vali (if path path :no-such-field))
;            value-atom (if value value value-a)
;            shadow-atom (if shadow-value
;                          shadow-value
;                          (if gen-shadow-field shadow-a value-atom))
;            global-vali-err (if vali-map
;                           (v/errors vali-path vali-map)
;                           (s/validation-errors vali-path))
;            local-vali-err (when vali-set
;                             (let [v (vali-set {:value @value-atom :shadow-value @shadow-atom})
;                                   err (into (v/errors :value v) (v/errors :shadow-value v))
;                                   field-key (keyword (str "value_" field-id))]
;                               (if-not (empty? err)
;                                 (s/merge-form-data-error {field-key (into #{} err)})
;                                 (s/remove-form-data-error-keys [field-key]))
;                               err))
;            field-errors (concat local-vali-err global-vali-err)
;            has-error (not (empty? field-errors))
;            has-label (some? label)
;            error-cls (if has-error "has-error has-danger" "")
;            row-cls (if has-label "row" "")
;            field-width-cls (str "col-12 col-sm-" field-width)
;            field-type-config (ftc/field-type-config type options {:to-val to-val
;                                                               :from-val from-val
;                                                               :value-atom value-atom
;                                                               :shadow-atom shadow-atom
;                                                               :has-shadow-field has-shadow-field
;                                                               })
;            props (r/merge-props options {
;                                          :value @shadow-atom
;                                          :class (if has-error "form-control-error" "")
;                                          :type (name (:type field-type-config))
;                                          :on-change (fn [v]
;                                                       (when (fn? (:on-change field-type-config))
;                                                         ((:on-change field-type-config) v))
;                                                       (validate-form))
;                                          })]
;       [:div.form-group {:class (str row-cls " " error-cls)}
;        (when has-label
;          [:label.col-12.col-sm-3.form-control-label label])
;        [:div {:class (if has-label field-width-cls "")}
;
;         ((:component field-type-config) props)
;
;         (if has-error
;           [:ul.text-muted.list-unstyled.m-b-0
;            (for [err field-errors]
;              ^{:key err}
;              [:li [:small err]])]
;           nil)
;         (if help
;           [:div.text-muted.text-small help])
;         ]]))))

;(defn fields
;  "Defines a form-group with N fields in it"
;  [& {:keys [fields] :as options}]
;  (let [{:keys [form-data]} (s/form-config-map)
;        fields-and-options
;        (map (fn [field]
;               (let [{:keys [value path shadow-value to-val from-val]} field
;                     key (enc/uuid-str 5)
;                     parse-type (or (:type field) (:parse-type field) :string)
;                     value-a (if path (r/cursor form-data (as-vec path)) nil)
;                     to-val (if (= type :checkbox)
;                              d/str-to-bool
;                              (or to-val #(ftc/parse-by-type parse-type %)))
;
;                     from-val (if (= type :checkbox)
;                                d/bool-to-str
;                                (or from-val #(str %)))
;
;                     gen-shadow-field (or (and to-val from-val)
;                                          (and parse-type (not= parse-type :string) (not value)))
;                     has-shadow-field (or gen-shadow-field shadow-value)
;                     shadow-a (if gen-shadow-field (r/atom (from-val (if value-a @value-a @value))) nil)
;
;                     ;value (if (:value field)
;                     ;          (:value field)
;                     ;            (r/cursor form-data (as-vec (:path field))))
;                     vali (if (:vali field) (:vali field) (:path field))
;                     ]
;                 (-> field
;                     (assoc :key key
;                            :parse-type parse-type
;                            :value-a value-a
;                            :to-val to-val
;                            :from-val from-val
;                            :gen-shadow-field gen-shadow-field
;                            :has-shadow-field has-shadow-field
;                            :shadow-a shadow-a
;                            :value value
;                            :vali vali)))) fields)]
;
;    (fn [& {:keys [label]}]
;      (let [has-line-label (some? label)
;            has-field-label (some? (map :label fields))
;            row-cls (if (or has-line-label has-field-label) "row" "")
;            ;line-errors (select-keys (s/validation-errors) (map :vali fields-with-key))
;            ;has-line-error false ;(not (empty? line-errors))
;            ;error-cls (if has-line-error "has-error" "")
;            ]
;        [:div.form-group {:class row-cls}
;         (when has-line-label
;           [:label.col-12.col-sm-3.form-control-label label])
;         (doall (for [field fields-and-options]
;             (let [{:keys [value vali label type help label-width field-width
;                           key to-val from-val has-shadow-field value-a gen-shadow-field shadow-a
;                           shadow-value vali-map vali-set path] :as options
;                    :or   {vali         :no-such-field
;                           type         :text
;                           label-width  3
;                           field-width  3
;                           key         (enc/uuid-str 5)}} field
;                   has-label (some? label)
;
;                   vali-path (if vali vali (if path path :no-such-field))
;                   value-atom (if value value value-a)
;                   shadow-atom (if shadow-value
;                                 shadow-value
;                                 (if gen-shadow-field shadow-a value-atom))
;                   global-vali-err (if vali-map
;                                     (v/errors vali-path vali-map)
;                                     (s/validation-errors vali-path))
;                   local-vali-err (when vali-set
;                                    (let [v (vali-set {:value @value-atom :shadow-value @shadow-atom})
;                                          err (into (v/errors :value v) (v/errors :shadow-value v))
;                                          field-key (keyword (str "value_" key))]
;                                      (if-not (empty? err)
;                                        (s/merge-form-data-error {field-key (into #{} err)})
;                                        (s/remove-form-data-error-keys [field-key]))
;                                      err))
;
;                   field-errors (concat local-vali-err global-vali-err)
;                   has-error (not (empty? field-errors))
;                   label-col-cls (str "col-12 col-sm-" label-width)
;                   field-col-cls (str "col-12 col-sm-" field-width)
;                   field-type-config (ftc/field-type-config type options {:to-val to-val
;                                                                      :from-val from-val
;                                                                      :value-atom value-atom
;                                                                      :shadow-atom shadow-atom
;                                                                      :has-shadow-field has-shadow-field
;                                                                      })
;                   props (r/merge-props options {
;                                                 :value @shadow-atom
;                                                 :class (if has-error "form-control-error" "")
;                                                 :type (name (:type field-type-config))
;                                                 :on-change (fn [v]
;                                                              (when (fn? (:on-change field-type-config))
;                                                                ((:on-change field-type-config) v))
;                                                              ;(reset! value (ftc/parse-by-type type (.-target.value v)))
;                                                              (validate-form))
;                                                 })]
;
;               ^{:key key}
;               (list
;                 (when has-label
;                   ^{:key 1}
;                   [:label.form-control-label {:class label-col-cls} label])
;                 ^{:key 2}
;                 [:div {:class (str field-col-cls " " (if has-error "has-error" ""))}
;                  ((:component field-type-config) props)
;                  (if has-error
;                    ^{:key 5}
;                    [:ul.text-muted.list-unstyled.m-b-0
;                     (for [err field-errors]
;                       ^{:key err}
;                       [:li [:small err]])]
;                    nil)
;                  (if help
;                    ^{:key 6}
;                    [:div.text-muted.text-small help])
;                  ]))))]))))


(defn static-field []
  (fn [& {:keys [label value label-cls value-cls type hide-empty] :as options
          :or {label-cls "col-3"
               value-cls "col-9"
               hide-empty false}}]
    (let [field-type-config (ftc/field-type-config type options {:value value})
          value-render-fn (:read-only-comp field-type-config)
          value-empty-fn (:is-empty field-type-config)
          rendered-value (value-render-fn value)]

      (when (or (not (value-empty-fn value)) (not hide-empty))
        (if (some? label)
          [:div.row
           [:label.form-control-label {:class label-cls} label]
           [:div {:class value-cls}
            rendered-value]]

          [:div.row
           [:div.col-12 rendered-value]])))))

(defn static-fields []
  (fn [& {:keys [label fields label-cls] :as options
          :or {label-cls "col-3"}}]
    (let [has-line-label (some? label)
          has-field-label (some? (map :label fields))
          hide-row-when-empty (every? #(:hide-empty %) fields)]


       (let [empty-stat-and-fields
             (doall (map (fn [field]
                    (let [{:keys [value label label-width field-width key type hide-empty] :as options
                       :or   {label-width  3
                              field-width  3
                              key         (enc/uuid-str 5)
                              type        :text
                              hide-empty  false}} field
                      has-label (some? label)
                      label-col-cls (str "col-12 col-sm-" label-width)
                      field-col-cls (str "col-12 col-sm-" field-width)
                      field-type-config (ftc/field-type-config type options {:value value})
                      value-render (:read-only-comp field-type-config)
                      value-empty-fn (:is-empty field-type-config)]

                      {:is-empty (value-empty-fn)
                       :render
                                 ^{:key key}
                                 (list
                                   (when (or (not (value-empty-fn)) (not hide-empty))
                                     ^{:key 1}
                                     (when has-label
                                       [:label.form-control-label {:class label-col-cls} label])
                                     ^{:key 2}
                                     [:div {:class field-col-cls} (value-render value)]))})) fields))

             fields-render (map :render empty-stat-and-fields)
             all-empty (reduce (fn [accu v] (and accu (:is-empty v))) true empty-stat-and-fields)]

         (when (or (not all-empty) (not hide-row-when-empty))
           [:div.form-group.row
            (when has-line-label
              [:label.form-control-label {:class label-cls} label])

            fields-render]

           )))))


(defn label-static-field [prompt value]
  [:div.form-group.row
   [:label.col-3.form-control-label prompt]
   [:div.col-9
    [:p.form-control-static value]]])
