;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.test.fixtures
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [mount.core :as mount]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.jwt :as jwt]
            [territory-bro.infra.jwt-test :as jwt-test]
            [territory-bro.infra.router :as router]
            [territory-bro.projections :as projections]))

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
               #'db/datasource
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
  (binding [config/env (merge config/env jwt-test/env)]
    (mount/start-with {#'jwt/jwk-provider jwt-test/fake-jwk-provider})
    (mount/start #'router/app)
    (f))
  (mount/stop))


(def *last-command (atom nil))

(defn fake-dispatcher [_conn _state command]
  (reset! *last-command command)
  [])

(defn fake-dispatcher-fixture [f]
  (reset! *last-command nil)
  (binding [dispatcher/command! fake-dispatcher]
    (f))
  (reset! *last-command nil))


(defmacro with-fixtures [fixtures & body]
  `(let [fixture# (clojure.test/join-fixtures ~fixtures)]
     (fixture# (fn []
                 (try
                   ~@body
                   (catch Throwable e#
                     (clojure.test/do-report {:type :error
                                              :message "Uncaught exception, not in assertion."
                                              :expected nil
                                              :actual e#})))))))
