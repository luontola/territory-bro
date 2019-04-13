;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [hugsql.core :as hugsql]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.congregation :refer :all]
            [territory-bro.db :as db])
  (:import (org.flywaydb.core Flyway)))

(defn db-fixture [f]
  (mount/stop) ; during interactive development, app might be running when tests start
  (mount/start-with-args {:test true}
                         #'config/env
                         #'db/databases)
  (f)
  (mount/stop))

(use-fixtures :once db-fixture)

(defn ^"[Ljava.lang.String;" strings [& strings]
  (into-array String strings))

(defn ^Flyway master-db-migrations [schema]
  (-> (Flyway/configure)
      (.dataSource (get-in db/databases [:default :datasource]))
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/master"))
      (.load)))

(defn ^Flyway tenant-db-migrations [schema]
  (-> (Flyway/configure)
      (.dataSource (get-in db/databases [:default :datasource]))
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/tenant"))
      (.load)))

(def queries (hugsql/map-of-db-fns "db/hugsql/congregation.sql"))

(defn query [conn name & params]
  (let [query-fn (get-in queries [name :fn])]
    (assert query-fn (str "query not found: " name))
    (apply query-fn conn params)))

(deftest my-congregations-test
  (let [master (master-db-migrations "test_master")
        tenant (tenant-db-migrations "test_tenant")]
    (.clean tenant)
    (.clean master)
    (.migrate master)
    (.migrate tenant)

    (let [conn (:default db/databases)]
      (jdbc/with-db-transaction [conn (:default db/databases) {:isolation :serializable}]
        (jdbc/execute! conn ["set search_path to test_tenant,test_master"])
        (is (= [] (jdbc/query conn ["select * from foo"])))
        (is (= [{:foo_id 1}] (query conn :create-foo {:name "hello"})))
        (is (= [{:foo_id 1, :name "hello"}] (query conn :find-foos)))
        (is (= {:bar_id 1} (jdbc/execute! conn ["insert into bar (bar_id) values (default)"] {:return-keys true})))
        (is (= [{:bar_id 1}] (jdbc/query conn ["select * from bar"]))))))

  (testing "lists congregations to which the user has access")
  (testing "hides congregations to which the user has no access")
  (testing "superadmin can access all congregations"))
