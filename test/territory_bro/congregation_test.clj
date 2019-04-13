;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
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

(deftest my-congregations-test
  (let [flyway (-> (Flyway/configure)
                   (.dataSource (get-in db/databases [:default :datasource]))
                   (.schemas (strings "test_master"))
                   (.locations (strings "classpath:migration/master"))
                   (.load))]
    (.migrate flyway)

    (let [conn (:default db/databases)]
      (jdbc/with-db-transaction [conn (:default db/databases) {:isolation :serializable}]
        (prn '--------------)
        (jdbc/execute! conn ["set search_path to test_master"])
        (prn 'before (jdbc/query conn ["select * from foo"]))
        (prn 'insert (jdbc/execute! conn ["insert into foo (foo_id) values (default)"]))
        (prn 'after (jdbc/query conn ["select * from foo"]))
        (prn '----------)))

    (.clean flyway))

  (is true)
  (testing "lists congregations to which the user has access")
  (testing "hides congregations to which the user has no access")
  (testing "superadmin can access all congregations"))
