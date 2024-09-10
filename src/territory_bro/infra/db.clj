;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.db
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hikari-cp.core :as hikari-cp]
            [hugsql.adapter.clojure-java-jdbc] ; for hugsql.core/get-adapter to not crash on first usage
            [hugsql.core :as hugsql]
            [mount.core :as mount]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]
            [territory-bro.infra.util :refer [getx]])
  (:import (clojure.lang IPersistentMap IPersistentVector)
           (com.zaxxer.hikari HikariDataSource)
           (java.net URL)
           (java.nio.file Paths)
           (java.sql Array Date PreparedStatement Timestamp)
           (java.time Instant)
           (org.flywaydb.core Flyway)
           (org.flywaydb.core.api FlywayException)
           (org.postgresql.util PGobject)))

(def expected-postgresql-version 16)
(def ^:dynamic *explain* false)

;; PostgreSQL error codes
;; https://www.postgresql.org/docs/11/errcodes-appendix.html
;; Some are also in org.postgresql.util.PSQLState but not all, so we list them here explicitly.
(def psql-serialization-failure "40001")
(def psql-deadlock-detected "40P01")
(def psql-undefined-object "42704")
(def psql-duplicate-object "42710")

(defn connect! ^HikariDataSource [database-url]
  (log/info "Connect" database-url)
  (hikari-cp/make-datasource {:jdbc-url database-url}))

(defn disconnect! [^HikariDataSource datasource]
  (log/info "Disconnect" (.getJdbcUrl datasource))
  (.close datasource))

(mount/defstate ^HikariDataSource datasource
  :start (connect! (getx config/env :database-url))
  :stop (disconnect! datasource))


;;;; SQL type conversions

(defn- array? [obj]
  (.isArray (class obj)))

(defn- array-to-vec
  "Converts multi-dimensional arrays to nested vectors."
  [obj]
  (if (array? obj)
    (mapv array-to-vec obj)
    obj))

(extend-protocol jdbc/IResultSetReadColumn
  Date
  (result-set-read-column [v _ _]
    (.toLocalDate v))

  Timestamp
  (result-set-read-column [v _ _]
    (.toInstant v))

  Array
  (result-set-read-column [v _ _]
    (array-to-vec (.getArray v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (json/read-value value)
        "jsonb" (json/read-value value)
        "citext" (str value)
        value))))

(extend-type Instant
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt idx]
    (.setTimestamp stmt idx (Timestamp/from v))))

(defn to-pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/write-value-as-string value))))

(extend-type IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt ^long idx]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= \_ (first type-name))
                           (apply str (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt idx (to-pg-json v))))))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))


;;;; Database schemas

(def ^:dynamic *clean-disabled* true)

(defn- ^String/1 strings [& ss]
  (into-array String ss))

(defn ^Flyway master-schema [schema]
  (-> (Flyway/configure)
      (.dataSource datasource)
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/master"))
      (.placeholders {"masterSchema" schema})
      (.cleanDisabled *clean-disabled*)
      (.load)))

(defn ^Flyway tenant-schema [schema master-schema]
  (-> (Flyway/configure)
      (.dataSource datasource)
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/tenant"))
      (.placeholders {"masterSchema" master-schema})
      (.cleanDisabled *clean-disabled*)
      (.load)))

(defn migrate-master-schema! []
  (let [schema (:database-schema config/env)]
    (log/info "Migrating master schema:" schema)
    (-> (master-schema schema)
        (.migrate))))

(defn migrate-tenant-schema! [schema]
  (log/info "Migrating tenant schema:" schema)
  (-> (tenant-schema schema (:database-schema config/env))
      (.migrate)))

(defn tenant-schema-up-to-date? [schema]
  (try
    (-> (tenant-schema schema (:database-schema config/env))
        (.validate))
    true
    (catch FlywayException _
      false)))

(defn get-schemas [conn]
  (->> (jdbc/query conn ["select schema_name from information_schema.schemata"])
       (map :schema_name)
       (doall)))

(defn generate-tenant-schema-name [conn cong-id]
  (let [master-schema (:database-schema config/env)
        tenant-schema (str master-schema
                           "_"
                           (str/replace (str cong-id) "-" ""))]
    (assert (not (contains? (set (get-schemas conn))
                            tenant-schema))
            {:schema-name tenant-schema})
    tenant-schema))

(defn set-search-path [conn schemas]
  (doseq [schema schemas]
    (assert (and schema (re-matches #"^[a-zA-Z0-9_]+$" schema))
            {:schema schema}))
  (jdbc/execute! conn [(str "set search_path to " (str/join "," schemas))]))

(def ^:private public-schema "public") ; contains PostGIS types

(defn use-master-schema [conn]
  (set-search-path conn [(:database-schema config/env)
                         public-schema]))

(defn use-tenant-schema [conn tenant-schema]
  (set-search-path conn [(:database-schema config/env)
                         tenant-schema
                         public-schema]))


;;;; Queries

(defmacro with-db [binding & body]
  (let [conn (first binding)
        options (second binding)]
    `(jdbc/with-db-transaction [~conn {:datasource datasource} (merge {:isolation :read-committed}
                                                                      ~options)]
       (use-master-schema ~conn)
       ~@body)))

(defn check-database-version [minimum-version]
  (with-db [conn {:read-only? true}]
    (let [metadata (-> (jdbc/db-connection conn) .getMetaData)
          version (.getDatabaseMajorVersion metadata)]
      (assert (>= version minimum-version)
              (str "Expected the database to be PostgreSQL " minimum-version " but it was "
                   (.getDatabaseProductName metadata) " " (.getDatabaseProductVersion metadata))))))

(defn auto-explain* [conn min-duration f]
  (jdbc/execute! conn ["LOAD 'auto_explain'"])
  (jdbc/execute! conn [(str "SET auto_explain.log_min_duration = " (int min-duration))])
  (jdbc/execute! conn ["SET auto_explain.log_analyze = true"])
  (jdbc/execute! conn ["SET auto_explain.log_buffers = true"])
  (jdbc/execute! conn ["SET auto_explain.log_triggers = true"])
  (jdbc/execute! conn ["SET auto_explain.log_verbose = true"])
  ;; will explain also the queries inside triggers, which territory-bro.infra.db/explain-query cannot do
  (jdbc/execute! conn ["SET auto_explain.log_nested_statements = true"])
  (let [result (f)]
    (jdbc/execute! conn ["SET auto_explain.log_min_duration = -1"])
    result))

(defmacro auto-explain [conn min-duration & body]
  `(auto-explain* ~conn ~min-duration (fn [] ~@body)))

(defn log-all-queries-in-this-transaction! [conn]
  (jdbc/query conn ["SELECT set_config('log_statement', 'all', true)"]))

(defn- explain-query [conn sql params]
  (jdbc/execute! conn ["SAVEPOINT explain_analyze"])
  (try
    ;; TODO: upgrade to PostgreSQL 12 and add SETTINGS to the options
    (->> (jdbc/query conn (cons (str "EXPLAIN (ANALYZE, VERBOSE, BUFFERS) " sql) params))
         (map (keyword "query plan")))
    (finally
      ;; ANALYZE will actually execute the query, so any side effects
      ;; must be rolled back to avoid executing them twice
      (jdbc/execute! conn ["ROLLBACK TO SAVEPOINT explain_analyze"]))))

(defn- prefix-join [prefix ss]
  (str prefix (str/join prefix ss)))

(defn- query! [conn queries-cache query-name params]
  (let [queries (queries-cache)
        query-fn (get-in queries [:db-fns query-name :fn])]
    (assert query-fn (str "Query not found: " query-name))
    (when *explain*
      (let [filename (.getFileName (Paths/get (.toURI ^URL (:resource queries))))
            sqlvec-fn (get-in queries [:sqlvec-fns query-name :fn])
            [sql & params] (apply sqlvec-fn params)
            query-plan (explain-query conn sql params)]
        (log/debug (str "SQL query " (name query-name) " in " filename ":\n"
                        sql
                        (when (not (empty? params))
                          (str "\n-- Parameters:"
                               (prefix-join "\n--\t" (map pr-str params))))
                        "\n-- Query plan:"
                        (prefix-join "\n--\t" query-plan)))))
    (apply query-fn conn params)))

(defn- load-queries [resource]
  {:db-fns (hugsql/map-of-db-fns resource {:quoting :ansi})
   :sqlvec-fns (hugsql/map-of-sqlvec-fns resource {:quoting :ansi
                                                   :fn-suffix ""})})

(defn compile-queries [path]
  (let [queries-cache (resources/auto-refresher (io/resource path) load-queries)]
    (fn [conn name & params]
      (query! conn queries-cache name params))))
