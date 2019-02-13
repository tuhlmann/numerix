(ns numerix.formatters.invoice
  (:require
    #?@(:clj [[clojure.pprint      :as pprint]
              [numerix.lib.helpers :as h :refer [tryo]]]
        :cljs [[cljs.pprint        :as pprint]])
              [taoensso.timbre     :as log])
  #?(:cljs
     (:require-macros [numerix.lib.helpers :refer [tryo]])))

(defn calc-net [invoice-line]
  (* (:amount invoice-line 0) (:quantity invoice-line 0)))

(defn calc-vat [invoice-line]
  (let [amount (calc-net invoice-line)
        vat (* (/ amount 100) (:vat invoice-line))]
    vat))

(defn calc-vat-total
  "Calculate the applicable amount of vat for this vat value"
  ([invoice-items vat]
   (calc-vat-total (filter #(= (:vat %) vat) invoice-items)))
  ([invoice-items]
   (reduce (fn [akku it] (+ akku (calc-vat it))) 0 invoice-items)))

(defn calc-net-total
  ([invoice-items vat]
   (calc-net-total (filter #(= (:vat %) vat) invoice-items)))
  ([invoice-items]
   (reduce (fn [akku it] (+ akku (calc-net it))) 0 invoice-items)))

(defn calc-gross-total
  ([invoice-items vat]
   (calc-gross-total (filter #(= (:vat %) vat) invoice-items)))
  ([invoice-items]
   (+ (calc-net-total invoice-items) (calc-vat-total invoice-items))))


(defn render-invoice-row-values [row]
  (merge row {:amount (tryo (pprint/cl-format nil "~,2f" (:amount row)) "")
              :net-amount (tryo (pprint/cl-format nil "~,2f" (calc-net row)) "")}))


(defn render-invoice-vat-items [invoice-items]
  (doall
    (for [vat-value (distinct (map :vat invoice-items))]
      {:vat-header
        (if (= vat-value 0)
          "non-taxable"
          (str vat-value "% Vat applied to"))

       :net-total
        (pprint/cl-format nil "~,2f" (calc-net-total invoice-items vat-value))

       :vat-total
        (if-not (= vat-value 0)
          (pprint/cl-format nil  "~,2f" (calc-vat-total invoice-items vat-value))
          "")

       :gross-total
        (pprint/cl-format nil  "~,2f" (calc-gross-total invoice-items vat-value))
       })))


(defn render-invoice-values [invoice]
  (let [rows (doall (map #(render-invoice-row-values %) (:invoice-items invoice)))
        inv (assoc invoice :invoice-items rows)
        inv (merge
              inv
              {:net-total (pprint/cl-format nil "~,2f" (calc-net-total (:invoice-items invoice)))
               :payable-amount (pprint/cl-format nil  "~,2f" (calc-gross-total (:invoice-items invoice)))
               :vat-items (render-invoice-vat-items (:invoice-items invoice))
               })
        ]
    inv
    ))
