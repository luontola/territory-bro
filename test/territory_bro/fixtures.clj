;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.fixtures
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.jwt :as jwt]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.projections :as projections]
            [territory-bro.router :as router]))

(defn- delete-schemas-starting-with! [conn prefix]
  (doseq [schema (db/get-schemas conn)
          :when (str/starts-with? schema prefix)]
    ;; TODO: there is no more gis_user table
    (when (:exists (first (jdbc/query conn ["SELECT to_regclass(?) AS exists" (str schema ".gis_user")])))
      (doseq [gis-user (jdbc/query conn [(str "SELECT username FROM " schema ".gis_user")])]
        (gis-db/drop-role-cascade! conn (:username gis-user) (db/get-schemas conn))))
    (jdbc/execute! conn [(str "DROP SCHEMA " schema " CASCADE")])))

(defn db-fixture [f]
  (mount/start #'config/env
               #'db/database
               #'projections/*cache)
  (let [schema (:database-schema config/env)]
    (assert (= "test_territorybro" schema)
            (str "Not the test database: " (pr-str schema)))
    ;; cleanup
    (db/with-db [conn {}]
      (delete-schemas-starting-with! conn schema))
    ;; setup
    (-> (db/master-schema schema)
        (.migrate))
    (-> (db/tenant-schema (str schema "_tenant") schema)
        (.migrate)))
  (f)
  (mount/stop))

(defn api-fixture [f]
  (mount/stop #'config/env)
  (mount/start-with-args jwt-test/env
                         #'config/env)
  (mount/start-with {#'jwt/jwk-provider jwt-test/fake-jwk-provider})
  (mount/start #'router/app)
  (f)
  (mount/stop))
