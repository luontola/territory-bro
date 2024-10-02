;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns kaocha.plugin.order
  (:require [kaocha.plugin :refer [defplugin]]))

(defn custom-order [test]
  (get-in test [:kaocha.testable/meta :order] 0))

(defn custom-order-sort [test-plan]
  (if-let [tests (:kaocha.test-plan/tests test-plan)]
    (assoc test-plan
           :kaocha.test-plan/tests
           (->> tests
                (sort-by custom-order)
                (map custom-order-sort)))
    test-plan))

(defplugin kaocha.plugin/order
  (post-load [test-plan]
             (custom-order-sort test-plan)))
