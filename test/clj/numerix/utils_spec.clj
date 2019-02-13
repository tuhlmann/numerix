(ns numerix.utils-spec
  (:require [clojure.test :refer :all]
            [numerix.utils :refer :all]))

(deftest test-simple-data-parsing
  (let [d (date "2009-01-22")]
    (is (= (day-from d) 22))))