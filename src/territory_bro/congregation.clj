;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.permissions :as perm])
  (:import (java.util UUID)))

(defn- format-tenant [id]
  ; TODO: human-readable name
  {:id id
   :name (.toUpperCase (name id))})

(defn my-congregations []
  (->> (perm/visible-congregations)
       (map format-tenant)
       (sort-by #(.toUpperCase (:name %)))))

;; new stuff

(def ^:private query! (db/compile-queries "db/hugsql/congregation.sql"))

(defn create-congregation! [conn name]
  (let [id (UUID/randomUUID)
        schema-name (str (:database-schema config/env)
                         "_"
                         (str/replace (str id) "-" ""))]
    (query! conn :create-congregation {:id id
                                       :name name
                                       :schema_name schema-name})
    (-> (db/tenant-schema schema-name)
        (.migrate))
    (log/info "Congregation created:" id)
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
