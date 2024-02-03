;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.resources-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.infra.resources :as resources]
            [territory-bro.test.testutil :refer [re-equals]])
  (:import (java.net URL)))

(def test-resource "db/flyway/master/R__functions.sql")

(deftest auto-refresh-test
  (let [*state (atom {:resource (io/resource test-resource)})
        *call-count-spy (atom 0)
        loader-fn (fn [x]
                    (swap! *call-count-spy inc)
                    (str "loaded " (not (str/blank? (slurp x)))
                         " " @*call-count-spy))]

    (testing "loads the resource on first call"
      (let [result (resources/auto-refresh *state loader-fn)]
        (is (= "loaded true 1" result))))

    (testing "reuses the old value if the resource has not changed"
      (let [result (resources/auto-refresh *state loader-fn)]
        (is (= "loaded true 1" result))))

    (testing "refreshes the value if the resource has changed"
      (swap! *state update ::resources/last-modified dec)
      (let [result (resources/auto-refresh *state loader-fn)]
        (is (= "loaded true 2" result))))

    (testing "reuses the old value if the resource temporarily disappears"
      (swap! *state update :resource (fn [^URL resource]
                                       (URL. (str resource ".no-such-file"))))
      (let [result (resources/auto-refresh *state loader-fn)]
        (is (= "loaded true 2" result))))

    (reset! *call-count-spy 0)

    (testing "if setting :resource, it is an error if it doesn't exist on startup"
      (reset! *state {:resource (io/resource "no-such-file")}) ; returns nil
      (is (thrown-with-msg?
           IllegalArgumentException (re-equals "Missing :resource and :resource-path")
           (resources/auto-refresh *state loader-fn))))

    (testing "if setting :resource-path, it will recover if the resource is missing on startup"
      (reset! *state {:resource-path "no-such-file"})
      (is (thrown-with-msg?
           IllegalStateException (re-equals "Resource not found: no-such-file")
           (resources/auto-refresh *state loader-fn))
          "file missing on startup")
      (swap! *state assoc :resource-path test-resource)
      (let [result (resources/auto-refresh *state loader-fn)]
        (is (= "loaded true 1" result)
            "recovers after file appears")))))
