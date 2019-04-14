;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.congregation :refer :all]
            [territory-bro.db :as db]))

(defn db-fixture [f]
  (mount/stop) ; during interactive development, app might be running when tests start
  (mount/start-with-args {:test true}
                         #'config/env
                         #'db/databases)
  (f)
  (mount/stop))

(use-fixtures :once db-fixture)

(defn delete-congregations! [conn]
  (doseq [congregation (jdbc/query conn ["select schema_name from congregation"])]
    (jdbc/execute! conn [(str "drop schema " (:schema_name congregation) " cascade")]))
  (jdbc/execute! conn ["delete from congregation"]))

(deftest congregations-test
  (let [master (master-db-migrations "test_master")
        tenant (tenant-db-migrations "test_tenant")]
    (.clean tenant)
    (.clean master)
    (.migrate master)
    (.migrate tenant))
  (jdbc/with-db-transaction [conn (:default db/databases) {:isolation :serializable}]

    (jdbc/execute! conn ["set search_path to test_tenant,test_master"])

    (testing "No congregations"
      (is (= [] (query conn :get-congregations))))

    (testing "Create congregation"
      (let [id (create-congregation! conn "foo" "foo_schema")]
        (is id)
        (is (= [{:congregation_id id, :name "foo", :schema_name "foo_schema"}]
               (query conn :get-congregations)))
        (is (= [] (jdbc/query conn ["select * from foo_schema.territory"]))
            "should create congregation schema")))
    (delete-congregations! conn))

  (testing "lists congregations to which the user has access")
  (testing "hides congregations to which the user has no access")
  (testing "superadmin can access all congregations"))
