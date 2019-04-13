;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.congregation :refer :all]
            [territory-bro.db :as db]))

(defn test-db-fixture [f]
  (mount/stop) ; during interactive development, app might be running when tests start
  (mount/start-with-args {:test true}
                         #'config/env
                         #'db/databases)
  ;(migrations/migrate ["reset"] {:database-url config/env})
  (f)
  (mount/stop))

(defn rollback-db-fixture [f]
  (binding [db/*conn* (:default db/databases)]
    (conman/with-transaction [db/*conn* {:isolation :serializable}]
      (jdbc/db-set-rollback-only! db/*conn*)
      (f))))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

; TODO: init database with https://flywaydb.org

(deftest my-congregations-test
  (assert (= [{:test 1}] (jdbc/query db/*db* ["select 1 as test"])))

  (is true)
  (testing "lists congregations to which the user has access")
  (testing "hides congregations to which the user has no access")
  (testing "superadmin can access all congregations"))
