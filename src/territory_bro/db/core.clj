; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db.core
  (:require [cheshire.core :refer [generate-string parse-string]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [conman.core :as conman]
            [environ.core :refer [env]]
            [ring.util.codec :refer [form-decode]])
  (:import org.postgresql.util.PGobject
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           (java.sql Date Timestamp PreparedStatement)
           (java.net URI)))

(defonce ^:dynamic *conn* (atom nil))

(conman/bind-connection *conn* "sql/queries.sql")

(defn create-territory! [opts]
  (-create-territory! (update opts :location json/write-str)))

(defn count-territories []
  (:count (first (-count-territories))))

(defn create-region! [opts]
  (-create-region! (update opts :location json/write-str)))

(defn count-regions []
  (:count (first (-count-regions))))

(defn connect! []
  ;; XXX: conman doesn't read the password from the jdbc url, so we must do it ourselves
  (let [query-params (-> (env :database-url)
                         URI.
                         .getSchemeSpecificPart
                         URI.
                         .getQuery
                         form-decode)
        pool-spec {:jdbc-url (env :database-url)
                   :password (get query-params "password")}]
    (reset! *conn* (conman/connect! pool-spec))))

(defn disconnect! []
  (conman/disconnect! *conn*))

(defn transactional* [f]
  (conman/with-transaction [*conn* {:isolation :serializable}]
                           (f)))

(defmacro transactional [& body]
  `(transactional* (fn [] ~@body)))

(defn to-date [sql-date]
  (-> sql-date (.getTime) (java.util.Date.)))

(extend-protocol jdbc/IResultSetReadColumn
  Date
  (result-set-read-column [v _ _] (to-date v))

  Timestamp
  (result-set-read-column [v _ _] (to-date v))

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

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))
