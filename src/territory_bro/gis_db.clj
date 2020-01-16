;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [medley.core :refer [remove-vals]]
            [schema.core :as s]
            [territory-bro.db :as db])
  (:import (java.util UUID Date)
           (org.postgresql.util PSQLException)))

(def ^:private query! (db/compile-queries "db/hugsql/gis.sql"))


;;; Regions

(defn- format-region [territory]
  (remove-vals nil? {:region/id (:id territory)
                     :region/name (:name territory)
                     :region/location (:location territory)}))

(defn get-congregation-boundaries [conn]
  (->> (query! conn :get-congregation-boundaries)
       (map format-region)
       (doall)))

(defn create-congregation-boundary! [conn location]
  (let [id (UUID/randomUUID)]
    (query! conn :create-congregation-boundary {:id id
                                                :location location})
    id))


(defn get-subregions [conn]
  (->> (query! conn :get-subregions)
       (map format-region)
       (doall)))

(defn create-subregion! [conn name location]
  (let [id (UUID/randomUUID)]
    (query! conn :create-subregion {:id id
                                    :name name
                                    :location location})
    id))


(defn get-card-minimap-viewports [conn]
  (->> (query! conn :get-card-minimap-viewports)
       (map format-region)
       (doall)))

(defn create-card-minimap-viewport! [conn location]
  (let [id (UUID/randomUUID)]
    (query! conn :create-card-minimap-viewport {:id id
                                                :location location})
    id))


;;; Territories

(defn- format-territory [territory]
  {:territory/id (:id territory)
   :territory/number (:number territory)
   :territory/addresses (:addresses territory)
   :territory/subregion (:subregion territory)
   :territory/meta (:meta territory)
   :territory/location (:location territory)})

(defn get-territories
  ([conn]
   (get-territories conn {}))
  ([conn search]
   (->> (query! conn :get-territories search)
        (map format-territory)
        (doall))))

(defn get-territory-by-id [conn id]
  (first (get-territories conn {:ids [id]})))

(defn create-territory! [conn territory]
  (let [id (UUID/randomUUID)]
    (query! conn :create-territory {:id id
                                    :number (:territory/number territory)
                                    :addresses (:territory/addresses territory)
                                    :subregion (:territory/subregion territory)
                                    :meta (:territory/meta territory)
                                    :location (:territory/location territory)})
    id))


;;; Changes

(s/defschema GisFeature
  {:id s/Str ; TODO: change to UUID
   :location s/Str
   (s/optional-key :name) s/Str
   (s/optional-key :number) s/Str
   (s/optional-key :subregion) s/Str
   (s/optional-key :addresses) s/Str
   (s/optional-key :meta) {s/Any s/Any}})

(s/defschema GisChange
  {:id s/Int
   :schema s/Str
   :table s/Str
   :op (s/enum "INSERT" "UPDATE" "DELETE") ; TODO: change to keywords
   :user s/Str
   :time Date ; TODO: change to Instant
   :old (s/maybe GisFeature)
   :new (s/maybe GisFeature)})

(def ^:private gis-change-validator (s/validator GisChange))

(defn- format-gis-change [change]
  (gis-change-validator change))

(defn get-changes
  ([conn]
   (get-changes conn {}))
  ([conn search]
   (->> (query! conn :get-gis-changes search)
        (map format-gis-change)
        (doall))))


;;; Database users

(defn user-exists? [conn username]
  (not (empty? (jdbc/query conn ["SELECT 1 FROM pg_roles WHERE rolname = ?" username]))))

(defn ensure-user-present! [conn {:keys [username password schema]}]
  (log/info "Creating GIS user:" username)
  (assert username)
  (assert password)
  (assert schema)
  (try
    (jdbc/execute! conn ["SAVEPOINT create_role"])
    (jdbc/execute! conn [(str "CREATE ROLE " username " WITH LOGIN")])
    (jdbc/execute! conn ["RELEASE SAVEPOINT create_role"])
    (catch PSQLException e
      ;; ignore error if role already exists
      (if (= db/psql-duplicate-object (.getSQLState e))
        (do
          (log/info "GIS user already present:" username)
          (jdbc/execute! conn ["ROLLBACK TO SAVEPOINT create_role"]))
        (throw e))))
  (jdbc/execute! conn [(str "ALTER ROLE " username " WITH PASSWORD '" password "'")])
  (jdbc/execute! conn [(str "ALTER ROLE " username " VALID UNTIL 'infinity'")])
  ;; TODO: move detailed permissions to schema specific role
  (jdbc/execute! conn [(str "GRANT USAGE ON SCHEMA " schema " TO " username)])
  (jdbc/execute! conn [(str "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE "
                            schema ".territory, "
                            schema ".congregation_boundary, "
                            schema ".subregion, "
                            schema ".card_minimap_viewport "
                            "TO " username)])
  nil)

(defn drop-role-cascade! [conn role schemas]
  (assert role)
  (try
    (doseq [schema schemas]
      (assert schema)
      (jdbc/execute! conn ["SAVEPOINT revoke_privileges"])
      (jdbc/execute! conn [(str "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA " schema " FROM " role)])
      (jdbc/execute! conn [(str "REVOKE USAGE ON SCHEMA " schema " FROM " role)])
      (jdbc/execute! conn ["RELEASE SAVEPOINT revoke_privileges"]))
    (catch PSQLException e
      ;; ignore error if role already does not exist
      (if (= db/psql-undefined-object (.getSQLState e))
        (do
          (log/info "GIS user already absent:" role)
          (jdbc/execute! conn ["ROLLBACK TO SAVEPOINT revoke_privileges"]))
        (throw e))))
  (jdbc/execute! conn [(str "DROP ROLE IF EXISTS " role)])
  nil)

(defn ensure-user-absent! [conn {:keys [username schema]}]
  (log/info "Deleting GIS user:" username)
  (drop-role-cascade! conn username [schema]))
