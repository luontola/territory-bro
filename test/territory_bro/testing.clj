;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.testing
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.jwt :as jwt]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.router :as handler]))

(defn api-fixture [f]
  (mount/stop) ; during interactive development, app might be running when tests start
  (mount/start-with-args jwt-test/env
                         #'config/env)
  (mount/start-with {#'jwt/jwk-provider jwt-test/fake-jwk-provider})
  (mount/start #'db/databases
               #'handler/app)
  (migrations/migrate ["migrate"] (select-keys config/env [:database-url]))
  (db/as-tenant nil
    (f))
  (mount/stop))

(defn transaction-rollback-fixture [f]
  (conman/with-transaction [db/*conn* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*conn*)
    (f)))
