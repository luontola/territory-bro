;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.foreign-key-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [territory-bro.infra.foreign-key :as foreign-key]
            [territory-bro.test.testutil :refer [re-equals]])
  (:import (java.util UUID)
           (territory_bro ValidationException)))

(deftest foreign-key-references-test
  (binding [foreign-key/*reference-checkers* {:foo #(= % (UUID. 0 1))}]
    (testing "valid value"
      (is (nil? (s/check {:foo/id (foreign-key/references :foo UUID)}
                         {:foo/id (UUID. 0 1)}))))

    (testing "explain schema"
      (is (= "{:foo/id (foreign-key/references :foo Uuid)}"
             (pr-str (s/explain {:foo/id (foreign-key/references :foo UUID)})))))

    (testing "validates the ID schema"
      (is (= "{:foo/id (not (instance? java.util.UUID 42))}"
             (pr-str (s/check {:foo/id (foreign-key/references :foo UUID)}
                              {:foo/id 42})))))

    (testing "validates the foreign key constraint"
      (is (= "{:foo/id (violated (foreign-key/references :foo #uuid \"00000000-0000-0000-0000-000000000666\"))}"
             (pr-str (s/check {:foo/id (foreign-key/references :foo UUID)}
                              {:foo/id (UUID. 0 0x666)})))))

    (testing "rethrows exceptions from checkers"
      (binding [foreign-key/*reference-checkers* {:foo #(throw (ValidationException. [[:no-such-foo %]]))}]
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:no-such-foo #uuid \"00000000-0000-0000-0000-000000000001\"]]")
             (s/validate {:foo/id (foreign-key/references :foo UUID)}
                         {:foo/id (UUID. 0 1)})))))

    (testing "error: checker for entity type missing"
      (is (thrown-with-msg?
           IllegalStateException (re-equals "No reference checker for :bar in territory-bro.infra.foreign-key/*reference-checkers*")
           (s/validate {:foo/id (foreign-key/references :bar UUID)}
                       {:foo/id (UUID. 0 1)}))))))
