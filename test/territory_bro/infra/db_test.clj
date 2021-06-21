;; Copyright © 2015-2021 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.infra.db-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.infra.db :as db]
            [territory-bro.test.fixtures :refer [db-fixture]]
            [territory-bro.test.testutil :refer [re-contains]])
  (:import (java.time Instant LocalDate)
           (java.util UUID)))

(use-fixtures :once db-fixture)

(deftest get-schemas-test
  (db/with-db [conn {:read-only? true}]
    (is (= ["test_territorybro"]
           (->> (db/get-schemas conn)
                (filter #{"test_territorybro"}))))))

(deftest generate-tenant-schema-name-test
  (db/with-db [conn {:read-only? true}]
    (is (= "test_territorybro_00000000000000000000000000000001"
           (db/generate-tenant-schema-name conn (UUID. 0 1))))))

(deftest check-database-version-test
  (let [current-version db/expected-postgresql-version]
    (is (nil? (db/check-database-version (dec current-version)))
        "newer than expected")
    (is (nil? (db/check-database-version current-version))
        "same as expected")
    (is (thrown-with-msg?
         AssertionError (re-contains "Expected the database to be PostgreSQL 14 but it was PostgreSQL 13")
         (db/check-database-version (inc current-version)))
        "older than expected")))

(deftest sql-type-conversions-test
  (db/with-db [conn {:read-only? true}]

    (testing "timestamp (with time zone)"
      (is (= [{:value (Instant/ofEpochSecond 2)}]
             (jdbc/query conn ["SELECT (?::timestamptz + interval '1 second')::timestamptz AS value"
                               (Instant/ofEpochSecond 1)]))))

    (testing "date"
      (is (= [{:value (LocalDate/of 2001 1 1)}]
             (jdbc/query conn ["SELECT (?::date + interval '1 day')::date AS value"
                               (LocalDate/of 2000 12 31)]))))

    (testing "json"
      (is (= [{:value {:foo "bar"}}]
             (jdbc/query conn ["SELECT ?::json AS value"
                               {:foo "bar"}]))))

    (testing "jsonb"
      (is (= [{:value {:foo "bar"}}]
             (jdbc/query conn ["SELECT ?::jsonb AS value"
                               {:foo "bar"}]))))

    (testing "array"
      (is (= [{:value [1 2 3]}]
             (jdbc/query conn ["SELECT '{1,2,3}'::integer[] AS value"]))
          "one way")
      (is (= [{:value [1 2 3]}]
             (jdbc/query conn ["SELECT ?::integer[] AS value"
                               [1 2 3]]))
          "round trip"))

    (testing "multi-dimensional array"
      ;; TODO: support for input parameters?
      (is (= [{:value [[1 2 3] [4 5 6]]}]
             (jdbc/query conn ["SELECT '{{1,2,3},{4,5,6}}'::integer[][] AS value"]))
          "one way"))))
