(ns numerix.validation.field-vali
  (:require
    [taoensso.timbre        :as log]
    [validateur.validation  :as v]
    [cuerdas.core           :as cue]))

(def pos-float-pattern #"^[+-]?[0-9]*\.?[0-9]{0,2}$")
(def pos-natural-pattern #"^[0-9]+$")

(def float-format-validations
  (v/validation-set
    (v/format-of :shadow-value :format pos-float-pattern :message "Needs numeric value")
    #_(v/validate-with-predicate
        :value
        (fn [{:keys [value shadow-value] :as p}]
          (log/info "Validating value " value " and " shadow-value ", whole set " (pr-str p))
          (= (str value) shadow-value)) :message "Amount must be equal")
    ))

(def natural-format-validations
  (v/validation-set
    (v/format-of :shadow-value :format pos-natural-pattern :message "Needs positive numeric value")
    ))
