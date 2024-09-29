;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.gis-db
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [medley.core :refer [map-keys]]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [territory-bro.infra.db :as db])
  (:import (java.time Instant)
           (java.util UUID)
           (org.postgresql.util PSQLException)))

(def ^:private queries (db/compile-queries "db/hugsql/gis.sql"))


;;;; Features

(defn- format-feature [feature]
  (map-keys #(keyword "gis-feature" (name %))
            feature))


;; TODO: not used in production code; remove?
(defn get-congregation-boundaries [conn]
  (->> (db/query! conn queries :get-congregation-boundaries)
       (mapv format-feature)))

(defn create-congregation-boundary! [conn location]
  (:id (db/query! conn queries :create-congregation-boundary {:id (UUID/randomUUID)
                                                              :location location})))


;; TODO: not used in production code; remove?
(defn get-regions [conn]
  (->> (db/query! conn queries :get-regions)
       (mapv format-feature)))

(defn create-region-with-id! [conn id name location]
  (:id (db/query! conn queries :create-region {:id id
                                               :name name
                                               :location location})))

(defn create-region! [conn name location]
  (create-region-with-id! conn (UUID/randomUUID) name location))


;; TODO: not used in production code; remove?
(defn get-card-minimap-viewports [conn]
  (->> (db/query! conn queries :get-card-minimap-viewports)
       (mapv format-feature)))

(defn create-card-minimap-viewport! [conn location]
  (:id (db/query! conn queries :create-card-minimap-viewport {:id (UUID/randomUUID)
                                                              :location location})))


;; TODO: not used in production code; remove?
(defn get-territories
  ([conn]
   (get-territories conn {}))
  ([conn search]
   (->> (db/query! conn queries :get-territories search)
        (mapv format-feature))))

(defn get-territory-by-id [conn id]
  (first (get-territories conn {:ids [id]})))

(defn create-territory! [conn territory]
  (:id (db/query! conn queries :create-territory {:id (UUID/randomUUID)
                                                  :number (:territory/number territory)
                                                  :addresses (:territory/addresses territory)
                                                  :subregion (:territory/region territory)
                                                  :meta (:territory/meta territory)
                                                  :location (:territory/location territory)})))


;;;; Changes

(s/defschema GisFeature
  {:id UUID
   :location s/Str
   (s/optional-key :name) s/Str
   (s/optional-key :number) s/Str
   (s/optional-key :subregion) s/Str
   (s/optional-key :addresses) s/Str
   (s/optional-key :meta) {s/Any s/Any}})

(s/defschema GisChange
  {:gis-change/id s/Int
   :gis-change/schema s/Str
   :gis-change/table (s/enum "territory" "congregation_boundary" "subregion" "card_minimap_viewport")
   :gis-change/op (s/enum :INSERT :UPDATE :DELETE)
   :gis-change/user s/Str
   :gis-change/time Instant
   :gis-change/old (s/maybe GisFeature)
   :gis-change/new (s/maybe GisFeature)
   :gis-change/processed? s/Bool})

(def ^:private gis-change-coercer
  (coerce/coercer! GisChange coerce/string-coercion-matcher))

(def ^:private column->key
  {:id :gis-change/id
   :schema :gis-change/schema
   :table :gis-change/table
   :op :gis-change/op
   :user :gis-change/user
   :time :gis-change/time
   :old :gis-change/old
   :new :gis-change/new
   :processed :gis-change/processed?})

(defn- format-gis-change [change]
  (->> change
       (map-keys #(get column->key % %))
       (gis-change-coercer)))

(defn get-changes
  ([conn]
   (get-changes conn {}))
  ([conn search]
   (->> (db/query! conn queries :get-gis-changes search)
        (mapv format-gis-change))))

(defn next-unprocessed-change [conn]
  (first (get-changes conn {:processed? false
                            :limit 1})))

(defn mark-changes-processed! [conn ids]
  (db/query! conn queries :mark-changes-processed {:ids ids}))


;;;; Database users

(defn user-exists? [conn username]
  (not (empty? (db/execute! conn ["SELECT 1 FROM pg_roles WHERE rolname = ?" username]))))

(defn ensure-user-present! [conn {:keys [username password schema]}]
  (log/info "Creating GIS user:" username)
  (assert username)
  (assert password)
  (assert schema)
  (try
    (db/execute-one! conn ["SAVEPOINT create_role"])
    (db/execute-one! conn [(str "CREATE ROLE " username " WITH LOGIN")])
    (db/execute-one! conn ["RELEASE SAVEPOINT create_role"])
    (catch PSQLException e
      ;; ignore error if role already exists
      (if (= db/psql-duplicate-object (.getSQLState e))
        (do
          (log/info "GIS user already present:" username)
          (db/execute-one! conn ["ROLLBACK TO SAVEPOINT create_role"]))
        (throw e))))
  (db/execute-one! conn [(str "ALTER ROLE " username " WITH PASSWORD '" password "'")])
  (db/execute-one! conn [(str "ALTER ROLE " username " VALID UNTIL 'infinity'")])
  ;; TODO: move detailed permissions to schema specific role
  (db/execute-one! conn [(str "GRANT USAGE ON SCHEMA " schema " TO " username)])
  (db/execute-one! conn [(str "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE "
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
      (db/execute-one! conn ["SAVEPOINT revoke_privileges"])
      (db/execute-one! conn [(str "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA " schema " FROM " role)])
      (db/execute-one! conn [(str "REVOKE USAGE ON SCHEMA " schema " FROM " role)])
      (db/execute-one! conn ["RELEASE SAVEPOINT revoke_privileges"]))
    (catch PSQLException e
      ;; ignore error if role already does not exist
      (if (= db/psql-undefined-object (.getSQLState e))
        (do
          (log/info "GIS user already absent:" role)
          (db/execute-one! conn ["ROLLBACK TO SAVEPOINT revoke_privileges"]))
        (throw e))))
  (db/execute-one! conn [(str "DROP ROLE IF EXISTS " role)])
  nil)

(defn ensure-user-absent! [conn {:keys [username schema]}]
  (log/info "Deleting GIS user:" username)
  (drop-role-cascade! conn username [schema]))

(defn- validate-grants [grants]
  (let [[{:keys [grantee table_schema]}] grants
        expected-grants (->> ["territory"
                              "subregion"
                              "congregation_boundary"
                              "card_minimap_viewport"]
                             (mapcat (fn [table_name]
                                       [{:table_name table_name :privilege_type "SELECT"}
                                        {:table_name table_name :privilege_type "INSERT"}
                                        {:table_name table_name :privilege_type "UPDATE"}
                                        {:table_name table_name :privilege_type "DELETE"}]))
                             (map #(-> %
                                       (assoc :grantee grantee)
                                       (assoc :table_schema table_schema))))]
    (when (= (set expected-grants)
             (set grants))
      {:username grantee
       :schema table_schema})))

(defn get-present-users [conn {:keys [username-prefix schema-prefix]}]
  (->> (db/query! conn queries :find-roles {:role (str username-prefix "%")
                                            :schema (str schema-prefix "%")})
       (group-by (juxt :grantee :table_schema))
       (vals)
       (mapv validate-grants)
       (filterv some?)))


;;;; Tenant schemas

(defn- schema-history-query [schema]
  (format "SELECT '%s' AS schema, * FROM %s.flyway_schema_history" schema schema))

(defn- simplify-schema-history [rows]
  (->> rows
       (sort-by :installed_rank)
       (remove #(= "SCHEMA" (:type %)))
       (map #(select-keys % [:version :type :script :checksum]))))

(defn get-present-schemas [conn {:keys [schema-prefix]}]
  (let [schemas (->> (db/query! conn queries :find-flyway-managed-schemas {:schema (str schema-prefix "%")})
                     (map :table_schema))
        reference-schema (->> schemas
                              (take 10) ; short-cut in case all tenant schemas are out of date
                              (filter db/tenant-schema-up-to-date?)
                              (first))]
    (when reference-schema
      (let [reference-history (simplify-schema-history (db/execute! conn [(schema-history-query reference-schema)]))]
        (->> (db/execute! conn [(->> schemas
                                     (map schema-history-query)
                                     (str/join " UNION ALL "))])
             (group-by :schema)
             (filter (fn [[_schema history]]
                       (= reference-history (simplify-schema-history history))))
             (keys))))))
