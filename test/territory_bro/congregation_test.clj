;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]))

(use-fixtures :once db-fixture)

(deftest congregations-test
  (jdbc/with-db-transaction [conn (:default db/databases) {:isolation :serializable}]
    (jdbc/execute! conn ["set search_path to test_tenant,test_master"]) ; TODO: hard coded shema name

    (testing "No congregations"
      (is (= [] (congregation/query conn :get-congregations))))

    (testing "Create congregation"
      ; TODO: hard coded shema name
      (let [id (congregation/create-congregation! conn "foo" "foo_schema")]
        (is id)
        (is (= [{:congregation_id id, :name "foo", :schema_name "foo_schema"}]
               (congregation/query conn :get-congregations)))
        (is (= [] (jdbc/query conn ["select * from foo_schema.territory"]))
            "should create congregation schema"))))

  (testing "lists congregations to which the user has access")
  (testing "hides congregations to which the user has no access")
  (testing "superadmin can access all congregations"))
