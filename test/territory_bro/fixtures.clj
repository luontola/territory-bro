;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.fixtures
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
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

(defn- delete-schemas-starting-with! [conn schema-name-prefix]
  (doseq [schema (jdbc/query conn ["select schema_name from information_schema.schemata"])
          :when (str/starts-with? (:schema_name schema) schema-name-prefix)]
    (jdbc/execute! conn [(str "drop schema \"" (:schema_name schema) "\" cascade")])))

(defn db-fixture [f]
  (mount/start-with-args {:test true}
                         #'config/env
                         #'db/databases)
  (jdbc/with-db-transaction [conn (:default db/databases) {:isolation :serializable}]
    ;; TODO: one prefix for all test schemas
    (delete-schemas-starting-with! conn "territorybro_test")
    (delete-schemas-starting-with! conn "test_master")
    (delete-schemas-starting-with! conn "test_tenant")
    (delete-schemas-starting-with! conn "foo_schema")
    (delete-schemas-starting-with! conn "congregation"))
  (migrations/migrate ["migrate"] (select-keys config/env [:database-url])) ; TODO: legacy code, remove me
  (let [master (congregation/master-db-migrations "test_master")] ;; TODO: hard coded schema name
    (.migrate master)
    (f))
  (mount/stop))

(defn api-fixture [f]
  (mount/stop #'config/env)
  (mount/start-with-args jwt-test/env
                         #'config/env)
  (mount/start-with {#'jwt/jwk-provider jwt-test/fake-jwk-provider})
  (mount/start #'handler/app)
  (db/as-tenant nil
    (f))
  (mount/stop))

(defn transaction-rollback-fixture [f]
  (conman/with-transaction [db/*conn* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*conn*)
    (f)))
