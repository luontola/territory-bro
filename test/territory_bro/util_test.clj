(ns territory-bro.util-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [territory-bro.util :refer :all])
  (:import (java.sql SQLException)))

(deftest test-fix-sqlexception-chain
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
