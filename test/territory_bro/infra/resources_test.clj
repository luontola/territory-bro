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

(deftest auto-refresh!-test
  (let [*state (resources/init-state (io/resource test-resource))
        *call-count-spy (atom 0)
        loader-fn (fn [x]
                    (swap! *call-count-spy inc)
                    (str "loaded " (not (str/blank? (slurp x)))
                         " " @*call-count-spy))]

    (testing "loads the resource on first call"
      (let [result (resources/auto-refresh! *state loader-fn)]
        (is (= "loaded true 1" result))))

    (testing "reuses the old value if the resource has not changed"
      (let [result (resources/auto-refresh! *state loader-fn)]
        (is (= "loaded true 1" result))))

    (testing "refreshes the value if the resource has changed"
      (swap! *state update ::resources/last-modified dec)
      (let [result (resources/auto-refresh! *state loader-fn)]
        (is (= "loaded true 2" result))))

    (testing "reuses the old value if the resource temporarily disappears"
      (swap! *state update ::resources/resource (fn [^URL resource]
                                                  (URL. (str resource ".no-such-file"))))
      (let [result (resources/auto-refresh! *state loader-fn)]
        (is (= "loaded true 2" result))))

    (reset! *call-count-spy 0)

    (testing "if providing a resource URL, it is an error if the file doesn't exist on startup"
      ;; not using `init-state` in this test, to bypass its nil check and ensure that auto-refresh! does the same check
      (reset! *state {::resources/resource (io/resource "no-such-file")}) ; returns nil
      (is (thrown-with-msg?
           IllegalArgumentException (re-equals "Resource must be an URL or string: nil")
           (resources/auto-refresh! *state loader-fn))))

    (testing "if providing a resource path, it will recover if the file is missing on startup"
      (reset! *state {::resources/resource "no-such-file"})
      (is (thrown-with-msg?
           IllegalStateException (re-equals "Resource not found: no-such-file")
           (resources/auto-refresh! *state loader-fn))
          "file missing on startup")
      (swap! *state assoc ::resources/resource test-resource)
      (let [result (resources/auto-refresh! *state loader-fn)]
        (is (= "loaded true 1" result)
            "recovers after file appears")))))

(deftest auto-refresher-test
  (let [loader-fn (fn [x]
                    (str "loaded " (not (str/blank? (slurp x)))))]

    (testing "resource can be an URL"
      (let [refresher (resources/auto-refresher (io/resource test-resource) loader-fn)]
        (is (= "loaded true" (refresher)))))

    (testing "resource can be a string"
      (let [refresher (resources/auto-refresher test-resource loader-fn)]
        (is (= "loaded true" (refresher)))))

    (testing "resource cannot be nil (e.g. when using io/resource and resource doesn't exist)"
      (is (thrown-with-msg?
           IllegalArgumentException (re-equals "Resource must be an URL or string: nil")
           (resources/auto-refresher nil loader-fn))))

    (testing "when resource is a string, the referenced file can me missing on startup"
      (let [refresher (resources/auto-refresher "no-such-file" loader-fn)]
        (is (thrown-with-msg?
             IllegalStateException (re-equals "Resource not found: no-such-file")
             (refresher)))))))
