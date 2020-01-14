;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-test
  (:require [clojure.test :refer :all]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.testutil :refer [re-contains]])
  (:import (java.util UUID)))

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
  (is (nil? (db/check-database-version 10))
      "newer than expected")
  (is (nil? (db/check-database-version 11))
      "same as expected")
  (is (thrown-with-msg? AssertionError (re-contains "Expected the database to be PostgreSQL 12 but it was PostgreSQL 11")
                        (db/check-database-version 12))
      "older than expected"))
