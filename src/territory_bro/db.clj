;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^{:resource-deps ["sql/queries.sql"]} territory-bro.db
  (:require [cheshire.core :refer [generate-string parse-string]]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [conman.core :as conman]
            [hikari-cp.core :as hikari-cp]
            [mount.core :as mount]
            [ring.util.codec :refer [form-decode]]
            [territory-bro.config :refer [env]]
            [territory-bro.util :refer [getx]])
  (:import (java.sql Date Timestamp PreparedStatement Array)
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           org.postgresql.util.PGobject
           (com.zaxxer.hikari HikariDataSource)
           (java.time Duration)))

; Since there are tens of tenants, we don't want to keep connections open to all of them all the time.
; See hikari-cp.core/default-datasource-options for available options.
(def datasource-options {:idle-timeout (-> (Duration/ofSeconds 60) (.toMillis))
                         :minimum-idle 0
                         :maximum-pool-size 10
                         :initialization-fail-timeout -1})

(defn my-connect! [pool-spec]
  ;; XXX: the hikari-cp wrapper doesn't support setInitializationFailTimeout
  (let [{:keys [initialization-fail-timeout]} pool-spec
        config (hikari-cp/datasource-config (conman/make-config pool-spec))]
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
  {:default (connect! (getx env :database-url))
   :tenant (into {} (map (fn [[tenant config]]
                           [tenant (connect! (:database-url config))])
                         (get env :tenant)))})

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

(defn transactional* [f]
  (conman/with-transaction [*conn* {:isolation :serializable}] (f)))

(defmacro transactional [& body]
  `(transactional* (fn [] ~@body)))

(defn to-date [sql-date]
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
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value))))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt idx]
    (.setTimestamp stmt idx (Timestamp. (.getTime v)))))

(defn to-pg-json [value]
  (doto (PGobject.)
        (.setType "jsonb")
        (.setValue (generate-string value))))

(extend-type clojure.lang.IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long idx]
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

(defmacro with-db [binding & body]
  ;; TODO: add congregation schema to search path
  `(jdbc/with-db-transaction [~(first binding) (:default databases) {:isolation :serializable}]
     (jdbc/execute! ~(first binding) [(str "set search_path to " (:database-schema env))])
     ~@body))
