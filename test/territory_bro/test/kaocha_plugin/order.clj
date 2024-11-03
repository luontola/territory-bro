(ns territory-bro.test.kaocha-plugin.order
  (:require [clojure.test :refer :all]
            [kaocha.plugin :refer [defplugin]]))

(defn- custom-order [test]
  (get-in test [:kaocha.testable/meta :order] 0))

(defn- do-not-skip-dependencies [tests]
  (if (every? :kaocha.testable/skip tests)
    tests
    (map (fn [test]
           (if (not (zero? (custom-order test)))
             (dissoc test :kaocha.testable/skip)
             test))
         tests)))

(defn custom-order-sort [test-plan]
  (if-some [tests (:kaocha.test-plan/tests test-plan)]
    (assoc test-plan
           :kaocha.test-plan/tests
           (->> tests
                (sort-by custom-order)
                (map custom-order-sort)
                (do-not-skip-dependencies)))
    test-plan))

(deftest custom-order-sort-test
  (testing "sorts the tests by :order metadata"
    (let [sorted-order [{:kaocha.testable/meta {:order -2}}
                        {:kaocha.testable/meta {:order -1}}
                        {:kaocha.testable/meta {}}
                        {:kaocha.testable/meta {:order 1}}]]
      (is (= {:kaocha.test-plan/tests sorted-order}
             (custom-order-sort
              {:kaocha.test-plan/tests (shuffle sorted-order)})))))

  (testing "the sorting is stable"
    (let [original-order [{:kaocha.testable/desc "A"}
                          {:kaocha.testable/desc "B"}
                          {:kaocha.testable/desc "C"}]]
      (is (= {:kaocha.test-plan/tests original-order}
             (custom-order-sort
              {:kaocha.test-plan/tests original-order})))))

  (testing "sorts recursively"
    (let [sorted-order [{:kaocha.testable/meta {:order 1}}
                        {:kaocha.testable/meta {:order 2}}]]
      (is (= {:kaocha.test-plan/tests [{:kaocha.test-plan/tests sorted-order}]}
             (custom-order-sort
              {:kaocha.test-plan/tests [{:kaocha.test-plan/tests (shuffle sorted-order)}]})))))

  (testing "when a focused test depends on skipped tests, will not skip them"
    (is (= {:kaocha.test-plan/tests [{:kaocha.testable/desc "setup-test"
                                      :kaocha.testable/meta {:order -1}}
                                     {:kaocha.testable/desc "skipped-test"
                                      :kaocha.testable/skip true}
                                     {:kaocha.testable/desc "focused-test"}]}
           (custom-order-sort
            {:kaocha.test-plan/tests [{:kaocha.testable/desc "setup-test"
                                       :kaocha.testable/skip true
                                       :kaocha.testable/meta {:order -1}}
                                      {:kaocha.testable/desc "skipped-test"
                                       :kaocha.testable/skip true}
                                      {:kaocha.testable/desc "focused-test"}]}))))

  (testing "will skip if all tests in the namespace are skipped"
    (let [test-plan {:kaocha.test-plan/tests [{:kaocha.testable/desc "setup-test"
                                               :kaocha.testable/skip true
                                               :kaocha.testable/meta {:order -1}}
                                              {:kaocha.testable/desc "skipped-test-1"
                                               :kaocha.testable/skip true}
                                              {:kaocha.testable/desc "skipped-test-2"
                                               :kaocha.testable/skip true}]}]
      (is (= test-plan (custom-order-sort test-plan))))))

(defplugin territory-bro.test.kaocha-plugin/order
  (post-load [test-plan]
             (custom-order-sort test-plan)))
