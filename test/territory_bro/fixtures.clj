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
            [territory-bro.db :as db]
            [territory-bro.jwt :as jwt]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.router :as router]))

(defn- delete-schemas-starting-with! [conn schema-name-prefix]
  (doseq [schema (jdbc/query conn ["select schema_name from information_schema.schemata"])
          :when (str/starts-with? (:schema_name schema) schema-name-prefix)]
    (jdbc/execute! conn [(str "drop schema \"" (:schema_name schema) "\" cascade")])))

(def test-env {:database-schema "test_territorybro"})

(defn db-fixture [f]
  (mount/start-with-args test-env
                         #'config/env
                         #'db/databases)
  (db/with-db [conn {}]
    (delete-schemas-starting-with! conn (:database-schema test-env)))
  (migrations/migrate ["migrate"] (select-keys config/env [:database-url])) ; TODO: legacy code, remove me
  (-> (db/master-schema (:database-schema config/env))
      (.migrate))
  (f)
  (mount/stop))

(defn api-fixture [f]
  (mount/stop #'config/env)
  (mount/start-with-args (merge test-env jwt-test/env)
                         #'config/env)
  (mount/start-with {#'jwt/jwk-provider jwt-test/fake-jwk-provider})
  (mount/start #'router/app)
  (db/as-tenant nil
    (f))
  (mount/stop))

(defn transaction-rollback-fixture [f]
  (conman/with-transaction [db/*conn* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*conn*)
    (f)))
