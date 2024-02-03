;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.resources-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.infra.resources :as resources]))

(def test-resource "db/flyway/master/R__functions.sql")

(deftest auto-refresh-test
  (let [*state (atom {:resource (io/resource test-resource)})
        *call-count-spy (atom 0)
        loader-fn (fn [x]
                    (swap! *call-count-spy inc)
                    {:data (str "loaded " (not (str/blank? (slurp x)))
                                " " @*call-count-spy)})]
    (testing "loads the resource on first call"
      (let [result (resources/auto-refresh *state loader-fn)]
        (is (= "loaded true 1" (:data result)))))

    (testing "reuses the output on the resource has not changed"
      (let [result (resources/auto-refresh *state loader-fn)]
        (is (= "loaded true 1" (:data result)))))

    (testing "refreshes the output if the resource has changed"
      (swap! *state update :last-modified dec)
      (let [result (resources/auto-refresh *state loader-fn)]
        (is (= "loaded true 2" (:data result)))))))
