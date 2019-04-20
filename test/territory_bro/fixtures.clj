;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.fixtures
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.jwt :as jwt]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.router :as handler]))

(defn delete-all-congregations! [conn]
  (doseq [congregation (jdbc/query conn ["select schema_name from congregation"])]
    (jdbc/execute! conn [(str "drop schema " (:schema_name congregation) " cascade")]))
  (jdbc/execute! conn ["delete from congregation"]))

(defn api-fixture [f]
  (mount/stop) ; during interactive development, app might be running when tests start
  (mount/start-with-args jwt-test/env
                         #'config/env)
  (mount/start-with {#'jwt/jwk-provider jwt-test/fake-jwk-provider})
  (mount/start #'db/databases
               #'handler/app)
  (migrations/migrate ["migrate"] (select-keys config/env [:database-url]))
  (let [master (congregation/master-db-migrations "test_master")]
    (.migrate master)
    (db/as-tenant nil
      (f))
    (jdbc/with-db-transaction [conn (:default db/databases) {:isolation :serializable}]
      (jdbc/execute! conn ["set search_path to test_master"]) ;; TODO: hard coded schema name
      (delete-all-congregations! conn))
    (.clean master))
  (mount/stop))

(defn transaction-rollback-fixture [f]
  (conman/with-transaction [db/*conn* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*conn*)
    (f)))
