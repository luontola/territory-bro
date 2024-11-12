(ns ^:slow territory-bro.infra.db-test
  (:require [clojure.test :refer :all]
            [territory-bro.infra.db :as db]
            [territory-bro.test.fixtures :refer [db-fixture]]
            [territory-bro.test.testutil :refer [re-contains thrown-with-msg?]])
  (:import (java.time Instant LocalDate)
           (java.util UUID)))

(use-fixtures :once db-fixture)

(deftest get-schemas-test
  (db/with-transaction [conn {:read-only true}]
    (is (= ["test_territorybro"]
           (->> (db/get-schemas conn)
                (filter #{"test_territorybro"}))))))

(deftest generate-tenant-schema-name-test
  (db/with-transaction [conn {:read-only true}]
    (is (= "test_territorybro_00000000000000000000000000000001"
           (db/generate-tenant-schema-name conn (UUID. 0 1))))))

(deftest check-database-version-test
  (let [current-version db/expected-postgresql-version]
    (is (nil? (db/check-database-version (dec current-version)))
        "allow newer than expected")
    (is (nil? (db/check-database-version current-version))
        "allow same as expected")
    (is (thrown-with-msg?
         AssertionError (re-contains "Expected the database to be PostgreSQL 17 but it was PostgreSQL 16")
         (db/check-database-version (inc current-version)))
        "error if older than expected")))

(deftest sql-type-conversions-test
  (db/with-transaction [conn {:read-only true}]

    (testing "timestamptz"
      (is (= {:value (Instant/ofEpochSecond 2)}
             (db/execute-one! conn ["SELECT (?::timestamptz + interval '1 second')::timestamptz AS value"
                                    (Instant/ofEpochSecond 1)]))))

    (testing "date"
      (is (= {:value (LocalDate/of 2001 1 1)}
             (db/execute-one! conn ["SELECT (?::date + interval '1 day')::date AS value"
                                    (LocalDate/of 2000 12 31)]))))

    (testing "json"
      (is (= {:value {:foo "bar"}}
             (db/execute-one! conn ["SELECT ?::json AS value"
                                    {:foo "bar"}]))
          "map")
      (is (= {:value ["foo" 123]}
             (db/execute-one! conn ["SELECT ?::json AS value"
                                    ["foo" 123]]))
          "vector")
      (is (= {:value "foo"}
             (db/execute-one! conn ["SELECT ?::json AS value"
                                    "\"foo\""]))
          "string"))

    (testing "jsonb"
      (is (= {:value {:foo "bar"}}
             (db/execute-one! conn ["SELECT ?::jsonb AS value"
                                    {:foo "bar"}]))
          "map")
      (is (= {:value ["foo" 123]}
             (db/execute-one! conn ["SELECT ?::jsonb AS value"
                                    ["foo" 123]]))
          "vector")
      (is (= {:value "foo"}
             (db/execute-one! conn ["SELECT ?::jsonb AS value"
                                    "\"foo\""]))
          "string"))

    (testing "array"
      (is (= {:value [1 2 3]}
             (db/execute-one! conn ["SELECT '{1,2,3}'::integer[] AS value"]))
          "one way")
      (is (= {:value [1 2 3]}
             (db/execute-one! conn ["SELECT ?::integer[] AS value"
                                    [1 2 3]]))
          "round trip"))

    (testing "multidimensional array"
      ;; TODO: support for input parameters?
      (is (= {:value [[1 2 3] [4 5 6]]}
             (db/execute-one! conn ["SELECT '{{1,2,3},{4,5,6}}'::integer[][] AS value"]))
          "one way"))))
