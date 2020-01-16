;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.foreign-key-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [territory-bro.foreign-key :as foreign-key]
            [territory-bro.testutil :refer [re-equals re-contains]])
  (:import (java.util UUID)))

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
      (is (= "{:foo/id (not (foreign-key/references :foo #uuid \"00000000-0000-0000-0000-000000000666\"))}"
             (pr-str (s/check {:foo/id (foreign-key/references :foo UUID)}
                              {:foo/id (UUID. 0 0x666)})))))

    (testing "error: checker for entity type missing"
      (is (thrown-with-msg?
           IllegalStateException (re-equals "No reference checker for :bar in territory-bro.foreign-key/*reference-checkers*")
           (s/check {:foo/id (foreign-key/references :bar UUID)}
                    {:foo/id (UUID. 0 1)}))))))
