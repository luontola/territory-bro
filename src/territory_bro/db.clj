;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^{:resource-deps ["sql/queries.sql"]} territory-bro.db
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [conman.core :as conman]
            [hikari-cp.core :as hikari-cp]
            [hugsql.core :as hugsql]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.util :refer [getx]])
  (:import (clojure.lang IPersistentMap IPersistentVector)
           (com.zaxxer.hikari HikariDataSource HikariConfig)
           (java.net URL)
           (java.sql Date Timestamp PreparedStatement Array)
           (java.time Duration)
           (org.flywaydb.core Flyway)
           (org.postgresql.util PGobject)))

; Since there are tens of tenants, we don't want to keep connections open to all of them all the time.
; See hikari-cp.core/default-datasource-options for available options.
(def datasource-options {:idle-timeout (-> (Duration/ofSeconds 60) (.toMillis))
                         :minimum-idle 0
                         :maximum-pool-size 10
                         :initialization-fail-timeout -1})

(defn my-connect! [pool-spec]
  ;; XXX: the hikari-cp wrapper doesn't support setInitializationFailTimeout
  (let [{:keys [initialization-fail-timeout]} pool-spec
        config ^HikariConfig (hikari-cp/datasource-config (conman/make-config pool-spec))]
    (when initialization-fail-timeout (.setInitializationFailTimeout config initialization-fail-timeout))
    {:datasource (HikariDataSource. config)}))

(defn connect! [database-url]
  (log/info "Connect" database-url)
  (my-connect! (merge datasource-options
                      {:jdbc-url database-url})))

(defn disconnect! [connection]
  (log/info "Disconnect" (if-let [ds (:datasource connection)]
                           (.getJdbcUrl ^HikariDataSource ds)))
  (conman/disconnect! connection))

(defn connect-all! []
  {:default (connect! (getx config/env :database-url))
   :tenant (into {} (map (fn [[tenant config]]
                           [tenant (connect! (:database-url config))])
                         (get config/env :tenant)))})

(defn disconnect-all! [databases]
  (disconnect! (:default databases))
  (doseq [config (vals (:tenant databases))]
    (disconnect! config)))

(mount/defstate databases
  :start (connect-all!)
  :stop (disconnect-all! databases))

(def ^:dynamic *conn*)

(def queries (conman/bind-connection-map nil "sql/queries.sql"))

(defn as-tenant* [tenant f]
  (binding [*conn* (if (nil? tenant)
                     (or (get databases :default)
                         (throw (IllegalArgumentException. "default database connection not found")))
                     (or (get-in databases [:tenant tenant])
                         (throw (IllegalArgumentException. (str "database connection for tenant " tenant " not found")))))]
    (f)))

(defmacro as-tenant [tenant & body]
  `(as-tenant* ~tenant (fn [] ~@body)))

(defn query
  ([query-id]
   (query query-id {}))
  ([query-id params]
   (if-let [query-fn (get-in queries [:fns query-id :fn])]
     (query-fn *conn* params)
     (throw (IllegalArgumentException. (str "query " query-id " not found"))))))

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
        "json" (cheshire/parse-string value true)
        "jsonb" (cheshire/parse-string value true)
        "citext" (str value)
        value))))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt idx]
    (.setTimestamp stmt idx (Timestamp. (.getTime v)))))

(defn to-pg-json [value]
  (doto (PGobject.)
        (.setType "jsonb")
        (.setValue (cheshire/generate-string value))))

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
      (.dataSource (get-in databases [:default :datasource]))
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/master"))
      (.load)))

(defn ^Flyway tenant-schema [schema]
  (-> (Flyway/configure)
      (.dataSource (get-in databases [:default :datasource]))
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/tenant"))
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
    `(jdbc/with-db-transaction [~conn (:default databases) ~options]
       (use-master-schema ~conn)
       ~@body)))

(defn- load-queries [queries-atom]
  ;; TODO: implement detecting resource changes to clojure.tools.namespace.repl/refresh
  (let [{queries :queries, resource :resource, old-last-modified :last-modified} @queries-atom
        new-last-modified (-> ^URL resource
                              (.openConnection)
                              (.getLastModified))]
    (if (= old-last-modified new-last-modified)
      queries
      (:queries (reset! queries-atom {:resource resource
                                      :queries (hugsql/map-of-db-fns resource)
                                      :last-modified new-last-modified})))))

(defn- query! [conn queries name params]
  (let [query-fn (get-in (load-queries queries) [name :fn])]
    (assert query-fn (str "Query not found: " name))
    (apply query-fn conn params)))

(defn compile-queries [path]
  (let [resource (io/resource path)
        _ (assert resource (str "Resource not found: " path))
        queries (atom {:resource resource})]
    (fn [conn name & params]
      (query! conn queries name params))))
