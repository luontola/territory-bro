;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.util-test
  (:require [clojure.test :refer :all]
            [territory-bro.infra.util :as util]
            [territory-bro.infra.util :refer [fix-sqlexception-chain getx]]
            [territory-bro.test.testutil :refer [re-equals thrown-with-msg?]])
  (:import (java.sql SQLException)))

(deftest fix-sqlexception-chain-test
  (testing "returns argument"
    (is (= nil (fix-sqlexception-chain nil)))
    (let [e (Exception.)]
      (is (= e (fix-sqlexception-chain e)))))

  (testing "adds next exception as cause"
    (let [next (SQLException. "next")
          e (doto (SQLException.)
              (.setNextException next))]
      (fix-sqlexception-chain e)
      (is (= next (.getCause e)))))

  (testing "adds next exception as suppressed if cause exists"
    (let [cause (SQLException. "cause")
          next (SQLException. "next")
          e (doto (SQLException.)
              (.initCause cause)
              (.setNextException next))]
      (fix-sqlexception-chain e)
      (is (= cause (.getCause e)))
      (is (= [next] (vec (.getSuppressed e))))))

  (testing "is recursive through cause"
    (let [next (SQLException. "next")
          e (doto (SQLException.)
              (.setNextException next))
          wrapper e
          wrapper (SQLException. wrapper)
          wrapper (RuntimeException. wrapper)]
      (fix-sqlexception-chain wrapper)
      (is (= next (.getCause e)))))

  (testing "is recursive through next"
    (let [next1 (SQLException. "next1")
          next2 (SQLException. "next2")
          e (doto (SQLException.)
              (.setNextException next1)
              (.setNextException next2))]
      (fix-sqlexception-chain e)
      (is (= next1 (.getCause e)))
      (is (= next2 (.getCause next1))))))

(deftest getx-test
  (testing "returns the value when the key exists"
    (is (= "some value" (getx {:some-key "some value"} :some-key))))

  (testing "supports all boolean values"
    (is (= true (getx {:some-key true} :some-key)))
    (is (= false (getx {:some-key false} :some-key))))

  (testing "throws an exception when key doesn't exist"
    (is (thrown-with-msg? IllegalArgumentException (re-equals "key :some-key is missing")
                          (getx {} :some-key))))

  (testing "throws an exception when the value is nil"
    (is (thrown-with-msg? IllegalArgumentException (re-equals "key :some-key is missing")
                          (getx {:some-key nil} :some-key)))))

(deftest natural-sort-by-test
  (let [expected [{:value ""}
                  {:value "1"}
                  {:value "2"} ; basic string sort would put this after "10"
                  {:value "10"}
                  {:value "10A"}
                  {:value "10b"} ; sorting should be case-insensitive
                  {:value "10C"}]]
    (is (= expected (util/natural-sort-by :value (shuffle expected)))))

  (testing "nil should not crash, but be treated the same as an empty string"
    (let [expected [{:value ""}
                    {:value nil} ; due to stable sort, should stay here in the middle
                    {:value ""}]]
      (is (= expected (util/natural-sort-by :value expected))))))
