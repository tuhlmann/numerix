(ns numerix.fields.field-type-config
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
            [re-frame.core :as rf]
            [numerix.validation.field-vali :as field-vali]
            [re-com.core :as re-com]))

;; Configurations for the different supported field types that can be passed into
;; field or static-field

(defn parse-by-type [type str-value]
  (condp = type
    :float
    (let [parsed (cue/parse-double str-value)]
      ;(log/info "parsed " str-value " to " parsed)
      (if (js/isNaN parsed) "" parsed))

    :number
    (let [parsed (cue/parse-int str-value)]
      (if (js/isNaN parsed) "" parsed))

    :textarea str-value
    str-value))



;; This part is @deprecated, for new features use the new re-frame compatible code below.
;(defn text-field-config [options config]
;  (let [{:keys [to-val value-atom shadow-atom has-shadow-field value]} config]
;    {:type :text
;     :on-change
;           (fn [v]
;             (do
;               (reset! shadow-atom (.-target.value v))
;               (when has-shadow-field
;                 (reset! value-atom (to-val (.-target.value v))))))
;     :component
;           (fn [props]
;             [:input.form-control (select-keys props [:on-change :value :label :type])])
;
;     :read-only-comp
;           (fn []
;             [:p.form-control-static value])
;
;     :is-empty
;           (fn [] (empty? (str value)))
;
;     }))

;(defn tag-field-config [options config]
;  (let [{:keys [categories add-new min-chars]
;         :or {add-new true
;              min-chars 0
;              categories []}} options
;        {:keys [value-atom value]} config]
;    {:type :text
;     :component
;           (fn [props]
;             [tag/tag-input
;              :add-new add-new
;              :min-chars min-chars
;              :categories categories
;              :tags value-atom])
;
;     :read-only-comp
;           (fn []
;             [tag/tag-list :tags value])
;
;     :is-empty
;           (fn [] (empty? value))
;
;
;     }))

;(defn quill-field-config [{:keys [edit-source-id] :as options} config]
;  (let [{:keys [value-atom value]} config
;        editor-id (h/short-rand)]
;    {:type :text
;     :component
;           (fn [props]
;             [quill/quill
;              :value value-atom
;              :editor-id editor-id
;              :on-editor-change (fn [quill html delta source]
;                                  (when (= source "user")
;                                    (when (satisfies? IDeref value-atom)
;                                      (reset! value-atom html)
;                                      (when (satisfies? IDeref edit-source-id)
;                                        (reset! edit-source-id editor-id))
;                                      )
;                                    )
;                                  )])
;
;     :read-only-comp
;           (fn []
;             [quill/as-html value])
;
;     :is-empty
;           (fn [] (empty? value))
;
;     }))

;(defn file-upload-field-config [options config]
;  (let [{:keys [multiple accept-input accept-match existing-files pending-files-fn remove-existing-fn]} options
;        {:keys [value-atom value]} config]
;    {:type :text
;     :component
;           (fn [props]
;             [file-upload/file-upload-field
;              :multiple multiple
;              :accept-input accept-input
;              :accept-match accept-match
;              :existing-files existing-files
;              :pending-files-fn pending-files-fn
;              :remove-existing-fn remove-existing-fn])
;
;     :read-only-comp
;           (fn []
;             [file-upload/file-list value])
;
;     :is-empty
;           (fn [] (empty? value))
;
;     }))

;(defn textarea-field-config [options config]
;  (let [{:keys [to-val value-atom shadow-atom has-shadow-field value]} config]
;    {:type :textarea
;     :on-change
;           (fn [v]
;             (do
;               (reset! shadow-atom (.-target.value v))
;               (when has-shadow-field
;                 (reset! value-atom (to-val (.-target.value v))))))
;     :component
;           (fn [props]
;             [:textarea.form-control (select-keys props [:on-change :value :label :type :rows :cols])])
;
;     :read-only-comp
;           (fn []
;             [:p.form-control-static value])
;
;     :is-empty
;           (fn [] (empty? value))
;
;     }))

;(defn checkbox-field-config [options config]
;  (let [{:keys [from-val to-val value-atom shadow-atom has-shadow-field value]} config]
;    {:type :checkbox
;     :on-change
;           (fn [v]
;             (do
;               (reset! shadow-atom (from-val (not @value-atom)))
;               (when has-shadow-field
;                 (reset! value-atom (to-val @shadow-atom)))))
;     :component
;           (fn [props]
;             [:span
;              [:input (let [p (r/merge-props props {:style {:vertical-align "bottom"}})
;                            _ (log/info "checkbox value atom is " @value-atom)
;                            p (if @value-atom
;                                (assoc  p :defaultChecked true)
;                                (dissoc p :defaultChecked)
;                                )
;                            p (select-keys p [:on-change :value :label :type :style :defaultChecked])]
;                        p)]])
;
;     :read-only-comp
;           (fn []
;             [:span
;              (let [p {:type :checkbox
;                       :disabled :disabled
;                       :style {:vertical-align "bottom"}}
;                    p (if value (assoc p :checked "checked") p)]
;                [:input.disabled p])])
;
;     :is-empty
;           (fn [] false)
;
;     }))

;(defn field-type-config [type options config]
;  (condp = type
;    :textarea
;    (textarea-field-config options config)
;
;    :checkbox
;    (checkbox-field-config options config)
;
;    :tag
;    (tag-field-config options config)
;
;    :quill
;    (quill-field-config options config)
;
;    :file-upload
;    (file-upload-field-config options config)
;
;    (text-field-config options config)
;    )
;  )

;;;
;;; NEW fieldtype config for re-frame compatible code.
;;; When all is migrated the upper code will be removed.

(defn component-map
  ([props keys display-value]
   (component-map props keys display-value nil))
  ([props keys display-value extra-props]
    (r/merge-props
      (select-keys props keys)
      (merge
        {:value display-value}
        extra-props))))

(defn text-field-config [options config]
  (let [{:keys [to-val from-val type parse-type label nested-field]} options
        {:keys [on-change value]} config
        parse-type (or type parse-type :string)
        ; if number field we create a string shadow field that shows the value
        to-val   (or to-val #(parse-by-type parse-type %))
        from-val (or from-val #(str %))
        inline-lbl? (and nested-field (some? label))
        on-change-inner (fn [v]
                          (when (fn? on-change)
                            (on-change (to-val (.-target.value v)))))

        ;display-value (from-val value)
        ]

    {:type :text
     ;:display-value display-value
     :display-value (fn [] (from-val (h/ref-or-val value)))

     :component
           (fn [props]
             [:input
              (component-map props
                             [:on-change :label :type]
                             (h/ref-or-val value)
                             {:type :text
                              :class (if inline-lbl? "form-control form-control-inline" "form-control")
                              :on-change on-change-inner})])

     :read-only-comp
           (fn [value]
             [:p.form-control-static value])

     :is-empty
           (fn [value]
             (empty? (str value)))

     }))

(defn password-field-config [options config]
  (let [{:keys [to-val from-val type parse-type]} options
        {:keys [on-change value]} config
        parse-type (or type parse-type :string)
        ; if number field we create a string shadow field that shows the value
        to-val   (or to-val #(parse-by-type parse-type %))
        from-val (or from-val #(str %))
        on-change-inner (fn [v]
                          (when (fn? on-change)
                            (on-change (to-val (.-target.value v)))))

        ]

    {:type :password
     :display-value (fn [] (from-val (h/ref-or-val value)))

     :component
           (fn [props]
             [:input.form-control (component-map props
                                                 [:on-change :label :type]
                                                 (h/ref-or-val value)
                                                 {:type :password
                                                  :on-change on-change-inner})])

     :read-only-comp
           (fn [value]
             [:p.form-control-static value])

     :is-empty
           (fn [value]
             (empty? (str value)))

     }))

(defn float-field-config [options config]
  (let [{:keys [to-val from-val type parse-type]} options
        {:keys [on-change value]} config
        parse-type (or type parse-type :string)
        ; if number field we create a string shadow field that shows the value
        to-val   (or to-val #(parse-by-type parse-type %))
        from-val (or from-val #(str %))
        shadow-atom (r/atom (from-val (h/ref-or-val value)))
        on-change-inner (fn [v]
                          (reset! shadow-atom (.-target.value v))
                          (let [v2 (to-val (.-target.value v))]
                            (when (and (fn? on-change) (not= (h/ref-or-val value) v2))
                              (on-change v2))))

        ]

    {:type :number
     :display-value (fn [] @shadow-atom)

     :vali-set (v/compose-sets
                 field-vali/float-format-validations)

     :component
           (fn [props]
             [:input.form-control (component-map props
                                                 [:on-change :label :type]
                                                 @shadow-atom
                                                 {:type :text
                                                  :on-change on-change-inner})])

     :read-only-comp
           (fn [value]
             [:p.form-control-static value])

     :is-empty
           (fn [value] (empty? (str value)))

     }))

(defn number-field-config [options config]
  (let [{:keys [to-val from-val type parse-type]} options
        {:keys [on-change value]} config
        parse-type (or type parse-type :string)
        ; if number field we create a string shadow field that shows the value
        to-val   (or to-val #(parse-by-type parse-type %))
        from-val (or from-val #(str %))
        shadow-atom (r/atom (from-val (h/ref-or-val value)))
        on-change-inner (fn [v]
                          (reset! shadow-atom (.-target.value v))
                          (let [v2 (to-val (.-target.value v))]
                            (when (and (fn? on-change) (not= (h/ref-or-val value) v2))
                              (on-change v2))))

        ]

    {:type :number
     :display-value (fn [] @shadow-atom)

     :vali-set (v/compose-sets
                 field-vali/natural-format-validations)

     :component
           (fn [props]
             [:input.form-control (component-map props
                                                 [:on-change :label :type]
                                                 @shadow-atom
                                                 {:type :text
                                                  :on-change on-change-inner})])

     :read-only-comp
           (fn [value]
             [:p.form-control-static value])

     :is-empty
           (fn [value] (empty? (str value)))

     }))

(defn textarea-field-config [options config]
  (let [{:keys [to-val from-val type parse-type]} options
        {:keys [on-change value]} config
        parse-type (or type parse-type :string)
        ; if number field we create a string shadow field that shows the value
        to-val   (or to-val #(parse-by-type parse-type %))
        from-val (or from-val #(str %))
        on-change-inner (fn [v]
                          (when (fn? on-change)(on-change (to-val (.-target.value v)))))
        display-value (fn [] (from-val value))]

    {:type :textarea
     :display-value display-value

     :component
           (fn [props]
             [:textarea.form-control (component-map props
                                                    [ :label :type :rows :cols]
                                                    (from-val @value)
                                                    {:type :text
                                                     :on-change on-change-inner})])

     :read-only-comp
           (fn []
             [:p.form-control-static (display-value)])

     :is-empty
           (fn [] (empty? (display-value)))

     }))

(defn checkbox-field-config [options config]
  (let [{:keys [from-val to-val type parse-type]} options
        {:keys [on-change value]} config
        ; if number field we create a string shadow field that shows the value
        ;to-val   d/str-to-bool
        ;from-val d/bool-to-str
        on-change-inner (fn [v]
                          (when (fn? on-change)
                            (on-change (not value))))]

    {:type :checkbox
     :display-value (fn [] value)

     ;:on-change
     ;      (fn [v]
     ;        (not value))

     :component
           (fn [props]
             [:span
              [:input (let [p (r/merge-props props {:style {:vertical-align "bottom"}
                                                    :type :checkbox
                                                    :on-change on-change-inner})
                            p (if value
                                (assoc  p :defaultChecked true)
                                (dissoc p :defaultChecked)
                                )
                            p (select-keys p [:on-change :value :label :type :style :defaultChecked])]
                        p)]])

     :read-only-comp
           (fn []
             [:span
              (let [p {:type :checkbox
                       :disabled :disabled
                       :style {:vertical-align "bottom"}}
                    p (if value (assoc p :checked "checked") p)]
                [:input.disabled p])])

     :is-empty
           (fn [] false)

     }))

;; FIXME: change tag field so it does not change the tag list itself but calls a on-change fn.
(defn tag-field-config [options config]
  (let [{:keys [categories add-new hide-single-category min-chars placeholder tag-icon-class]
         :or {add-new true
              min-chars 0
              categories (r/atom [])
              placeholder "Add a tag"
              hide-single-category true
              tag-icon-class "fa fa-tags"}} options
        {:keys [value on-change]} config
        on-change-inner (fn [all-tags]
                          (when (fn? on-change)
                            (on-change all-tags)))]
    {
     :display-value (fn [] value)
     :type :text
     :component
           (fn [props]
             [tag/tag-input
              :add-new add-new
              :min-chars min-chars
              :categories @categories
              :placeholder placeholder
              :hide-single-category hide-single-category
              :tags (h/ref-or-val value)
              :on-tag-deleted (fn [idx]
                                (on-change-inner (h/vec-remove (h/ref-or-val value) idx)))
              :on-change on-change-inner
              ])

     :read-only-comp
           (fn []
             [tag/tag-list :tags value :tag-icon-class tag-icon-class])

     :is-empty
           (fn [] (empty? value))


     }))

(defn quill-field-config [{:keys [edit-source-id] :as options} config]
  (let [{:keys [value on-change]} config
        editor-id (h/short-rand)
        ]

    {:type :text

     :display-value (fn [] value)

     :component
           (fn [props]
             [quill/quill
              :value value
              :editor-id editor-id
              :on-editor-change (fn [quill html delta source]
                                  (when (and (= source "user") (fn? on-change))
                                    (on-change html)
                                    (when (satisfies? IDeref edit-source-id)
                                      (reset! edit-source-id editor-id))))])

     :read-only-comp
           (fn []
             [quill/as-html value])

     :is-empty
           (fn [] (empty? value))

     }))

(defn date-field-config [options config]
  (let [{:keys [to-val from-val type parse-type label nested-field]} options
        {:keys [on-change value]} config
        parse-type (or type parse-type :string)
        ; if number field we create a string shadow field that shows the value
        to-val   (or to-val #(parse-by-type parse-type %))
        from-val (or from-val #(str %))
        inline-lbl? (and nested-field (some? label))
        on-change-inner (fn [v]
                          (when (fn? on-change)
                            (on-change v)))

        ;display-value (from-val value)
        ]

    {:type          :date
     ;:display-value display-value
     :display-value (fn [] (from-val (h/ref-or-val value)))

     :component
                    (fn [props]
                      [re-com/datepicker-dropdown
                       :model value
                       :format "dd.MM.yyyy"
                       :show-today? true
                       :on-change on-change-inner
                       ]
                      ;[:input
                      ; (component-map props
                      ;                [:on-change :label :type]
                      ;                (h/ref-or-val value)
                      ;                {:type      :text
                      ;                 :class     (if inline-lbl? "form-control form-control-inline" "form-control")
                      ;                 :on-change on-change-inner})]
                      )

     :read-only-comp
                    (fn [value]
                      [:p.form-control-static value])

     :is-empty
                    (fn [value]
                      (empty? (str value)))

     }))


(defn file-upload-field-config [options config]
  (let [{:keys [multiple accept-input accept-match existing-files pending-files-fn remove-existing-fn]} options
        {:keys [value]} config]

    {:type :text

     :display-value (fn [] value)

     :component
           (fn [props]
             [file-upload/file-upload-field
              :multiple multiple
              :accept-input accept-input
              :accept-match accept-match
              :existing-files existing-files
              :pending-files-fn pending-files-fn
              :remove-existing-fn remove-existing-fn])

     :read-only-comp
           (fn []
             [file-upload/file-list value])

     :is-empty
           (fn [] (empty? value))

     }))


(defn field-type-config [type options config]
  (case type
    :textarea
    (textarea-field-config options config)

    :checkbox
    (checkbox-field-config options config)

    :tag
    (tag-field-config options config)

    :quill
    (quill-field-config options config)

    :file-upload
    (file-upload-field-config options config)

    :float
    (float-field-config options config)

    :number
    (number-field-config options config)

    :password
    (password-field-config options config)

    :date
    (date-field-config options config)

    ;; string field
    (text-field-config options config)
    )
  )
