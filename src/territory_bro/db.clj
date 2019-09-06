;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [conman.core :as conman]
            [hugsql.adapter.clojure-java-jdbc] ; for hugsql.core/get-adapter to not crash on first usage
            [hugsql.core :as hugsql]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.json :as json]
            [territory-bro.util :refer [getx]])
  (:import (clojure.lang IPersistentMap IPersistentVector)
           (com.zaxxer.hikari HikariDataSource)
           (java.net URL)
           (java.sql Date Timestamp PreparedStatement Array)
           (org.flywaydb.core Flyway)
           (org.postgresql.util PGobject)))

;; PostgreSQL error codes
;; https://www.postgresql.org/docs/11/errcodes-appendix.html
(def psql-serialization-failure "40001")
(def psql-undefined-object "42704")
(def psql-duplicate-object "42710")

(defn connect! [database-url]
  (log/info "Connect" database-url)
  (conman/connect! {:jdbc-url database-url}))

(defn disconnect! [connection]
  (log/info "Disconnect" (if-let [ds (:datasource connection)]
                           (.getJdbcUrl ^HikariDataSource ds)))
  (conman/disconnect! connection))

(mount/defstate database
  :start (connect! (getx config/env :database-url))
  :stop (disconnect! database))

(defn to-date [^java.util.Date sql-date]
  (-> sql-date (.getTime) (java.util.Date.)))

(extend-protocol jdbc/IResultSetReadColumn
  Date
  (result-set-read-column [v _ _] (to-date v))

  Timestamp
  (result-set-read-column [v _ _] (to-date v))

  Array
  (result-set-read-column [v _ _] (vec (.getArray v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (json/parse-string value)
        "jsonb" (json/parse-string value)
        "citext" (str value)
        value))))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt idx]
    (.setTimestamp stmt idx (Timestamp. (.getTime v)))))

(defn to-pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/generate-string value))))

(extend-type IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt ^long idx]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt idx (to-pg-json v))))))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))

;; new stuff

(defn- ^"[Ljava.lang.String;" strings [& strings]
  (into-array String strings))

(defn ^Flyway master-schema [schema]
  (-> (Flyway/configure)
      (.dataSource (:datasource database))
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/master"))
      (.load)))

(defn ^Flyway tenant-schema [schema master-schema]
  (-> (Flyway/configure)
      (.dataSource (:datasource database))
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/tenant"))
      (.placeholders {"masterSchema" master-schema})
      (.load)))

(defn get-schemas [conn]
  (->> (jdbc/query conn ["select schema_name from information_schema.schemata"])
       (map :schema_name)
       (doall)))

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

(defmacro with-db [binding & body]
  (let [conn (first binding)
        options (merge {:isolation :serializable}
                       (second binding))]
    `(jdbc/with-db-transaction [~conn database ~options]
       (use-master-schema ~conn)
       ~@body)))

(defn check-database-version [minimum-version]
  (with-db [conn {}]
    (let [metadata (-> (jdbc/db-connection conn) .getMetaData)
          version (.getDatabaseMajorVersion metadata)]
      (assert (>= version minimum-version)
              (str "Expected the database to be PostgreSQL " minimum-version " but it was "
                   (.getDatabaseProductName metadata) " " (.getDatabaseProductVersion metadata))))))

(defn- load-queries [*queries]
  ;; TODO: implement detecting resource changes to clojure.tools.namespace.repl/refresh
  (let [{queries :queries, resource :resource, old-last-modified :last-modified} @*queries
        new-last-modified (-> ^URL resource
                              (.openConnection)
                              (.getLastModified))]
    (if (= old-last-modified new-last-modified)
      queries
      (:queries (reset! *queries {:resource resource
                                  :queries (hugsql/map-of-db-fns resource)
                                  :last-modified new-last-modified})))))

(defn- query! [conn *queries name params]
  (let [query-fn (get-in (load-queries *queries) [name :fn])]
    (assert query-fn (str "Query not found: " name))
    (apply query-fn conn params)))

(defn compile-queries [path]
  (let [resource (io/resource path)
        _ (assert resource (str "Resource not found: " path))
        *queries (atom {:resource resource})]
    (fn [conn name & params]
      (query! conn *queries name params))))
