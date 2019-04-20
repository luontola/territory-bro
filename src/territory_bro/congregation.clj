;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [hugsql.core :as hugsql]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.permissions :as perm])
  (:import (java.net URL)
           (java.util UUID)
           (org.flywaydb.core Flyway)))

;; Old stuff

(defn- format-tenant [id]
  ; TODO: human-readable name
  {:id id
   :name (.toUpperCase (name id))})

(defn my-congregations []
  (->> (perm/visible-congregations)
       (map format-tenant)
       (sort-by #(.toUpperCase (:name %)))))

;; New stuff

(defn ^"[Ljava.lang.String;" strings [& strings]
  (into-array String strings))

(defn ^Flyway master-db-migrations [schema]
  (-> (Flyway/configure)
      (.dataSource (get-in db/databases [:default :datasource]))
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/master"))
      (.load)))

(defn ^Flyway tenant-db-migrations [schema]
  (-> (Flyway/configure)
      (.dataSource (get-in db/databases [:default :datasource]))
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/tenant"))
      (.load)))

(def congregation-queries (atom {:resource (io/resource "db/hugsql/congregation.sql")}))

(defn load-queries []
  ;; TODO: implement detecting resource changes to clojure.tools.namespace.repl/refresh
  (let [{:keys [queries resource last-modified]} @congregation-queries
        current-last-modified (-> ^URL resource
                                  (.openConnection)
                                  (.getLastModified))]
    (if (= last-modified current-last-modified)
      queries
      (:queries (reset! congregation-queries
                        {:resource resource
                         :queries (hugsql/map-of-db-fns resource)
                         :last-modified current-last-modified})))))

(defn query! [conn name & params]
  (let [query-fn (get-in (load-queries) [name :fn])]
    (assert query-fn (str "query not found: " name))
    (apply query-fn conn params)))

(defn create-congregation! [conn name]
  (let [id (UUID/randomUUID)
        schema-name (str (:database-schema config/env)
                         "_"
                         (str/replace (str id) "-" ""))
        flyway (tenant-db-migrations schema-name)]
    (query! conn :create-congregation {:id id
                                       :name name
                                       :schema_name schema-name})
    (.migrate flyway)
    (log/info "Created congregation" id)
    id))

(defn- format-congregation [row]
  {::id (:id row)
   ::name (:name row)
   ::schema-name (:schema_name row)})

(defn get-congregations
  ([conn]
   (get-congregations conn {}))
  ([conn search]
   (->> (query! conn :get-congregations search)
        (map format-congregation)
        (doall))))

(defn get-congregation [conn id]
  (first (get-congregations conn {:ids [id]})))
