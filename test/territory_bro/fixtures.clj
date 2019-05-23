;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.fixtures
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.events :as events]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.jwt :as jwt]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.router :as router]))

(defn- delete-schemas-starting-with! [conn prefix]
  (doseq [schema (db/get-schemas conn)
          :when (str/starts-with? schema prefix)]
    (when (:exists (first (jdbc/query conn ["SELECT to_regclass(?) AS exists" (str schema ".gis_user")])))
      (doseq [gis-user (jdbc/query conn [(str "SELECT username FROM " schema ".gis_user")])]
        (gis-user/drop-role-cascade! conn (:username gis-user) (db/get-schemas conn))))
    (jdbc/execute! conn [(str "DROP SCHEMA " schema " CASCADE")])))

(def test-env {:database-schema "test_territorybro"})

(defn db-fixture [f]
  (mount/start-with-args test-env
                         #'config/env
                         #'db/databases)
  ;; cleanup
  (db/with-db [conn {}]
    (delete-schemas-starting-with! conn (:database-schema test-env)))
  ;; setup
  (-> (db/master-schema (:database-schema config/env))
      (.migrate))
  (-> (db/tenant-schema (str (:database-schema config/env) "_tenant")
                        (:database-schema config/env))
      (.migrate))
  (f)
  (mount/stop))

(defn api-fixture [f]
  (mount/stop #'config/env)
  (mount/start-with-args (merge test-env jwt-test/env)
                         #'config/env)
  (mount/start-with {#'jwt/jwk-provider jwt-test/fake-jwk-provider})
  (mount/start #'router/app)
  (f)
  (mount/stop))

(defn event-actor-fixture [f]
  (binding [events/*current-system* "test"]
    (f)))
