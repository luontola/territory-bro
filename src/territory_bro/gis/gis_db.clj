;; Copyright © 2015-2021 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.gis-db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [medley.core :refer [map-keys remove-vals]]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [territory-bro.infra.db :as db])
  (:import (java.time Instant)
           (java.util UUID)
           (org.postgresql.util PSQLException)))

(def ^:private query! (db/compile-queries "db/hugsql/gis.sql"))


;;;; Features

(defn- format-feature [feature]
  (map-keys #(keyword "gis-feature" (name %))
            feature))


;; TODO: not used in production code; remove?
(defn get-congregation-boundaries [conn]
  (->> (query! conn :get-congregation-boundaries)
       (map format-feature)
       (doall)))

(defn create-congregation-boundary! [conn location]
  (let [id (UUID/randomUUID)]
    (query! conn :create-congregation-boundary {:id id
                                                :location location})
    id))


;; TODO: not used in production code; remove?
(defn get-regions [conn]
  (->> (query! conn :get-regions)
       (map format-feature)
       (doall)))

(defn create-region-with-id! [conn id name location]
  (query! conn :create-region {:id id
                               :name name
                               :location location})
  id)

(defn create-region! [conn name location]
  (create-region-with-id! conn (UUID/randomUUID) name location))


;; TODO: not used in production code; remove?
(defn get-card-minimap-viewports [conn]
  (->> (query! conn :get-card-minimap-viewports)
       (map format-feature)
       (doall)))

(defn create-card-minimap-viewport! [conn location]
  (let [id (UUID/randomUUID)]
    (query! conn :create-card-minimap-viewport {:id id
                                                :location location})
    id))


;; TODO: not used in production code; remove?
(defn get-territories
  ([conn]
   (get-territories conn {}))
  ([conn search]
   (->> (query! conn :get-territories search)
        (map format-feature)
        (doall))))

(defn get-territory-by-id [conn id]
  (first (get-territories conn {:ids [id]})))

(defn create-territory! [conn territory]
  (let [id (UUID/randomUUID)]
    (query! conn :create-territory {:id id
                                    :number (:territory/number territory)
                                    :addresses (:territory/addresses territory)
                                    :subregion (:territory/region territory)
                                    :meta (:territory/meta territory)
                                    :location (:territory/location territory)})
    id))


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
   :gis-change/processed? s/Bool
   :gis-change/replacement-id (s/maybe s/Uuid)})

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
   :processed :gis-change/processed?
   :replacement_id :gis-change/replacement-id})

(defn- format-gis-change [change]
  (->> change
       (map-keys #(get column->key % %))
       (gis-change-coercer)))

(defn get-changes
  ([conn]
   (get-changes conn {}))
  ([conn search]
   (->> (query! conn :get-gis-changes search)
        (map format-gis-change)
        (doall))))

(defn next-unprocessed-change [conn]
  (first (get-changes conn {:processed? false
                            :limit 1})))

(defn mark-changes-processed! [conn ids]
  (query! conn :mark-changes-processed {:ids ids}))

(defn replace-id! [conn schema table old-id new-id]
  (query! conn :replace-id-of-entity {:schema_table (str schema "." table)
                                      :old_id old-id
                                      :new_id new-id})
  (query! conn :replace-id-of-changes {:schema schema
                                       :table table
                                       :old_id old-id
                                       :new_id new-id}))


;;;; Database users

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
  (->> (query! conn :find-roles {:role (str username-prefix "%")
                                 :schema (str schema-prefix "%")})
       (group-by (juxt :grantee :table_schema))
       (vals)
       (map validate-grants)
       (filter some?)
       (doall)))


;;;; Tenant schemas

(defn- schema-history-query [schema]
  (format "SELECT '%s' AS schema, * FROM %s.flyway_schema_history" schema schema))

(defn- simplify-schema-history [rows]
  (->> rows
       (sort-by :installed_rank)
       (remove #(= "SCHEMA" (:type %)))
       (map #(select-keys % [:version :type :script :checksum]))))

(defn get-present-schemas [conn {:keys [schema-prefix]}]
  (let [schemas (->> (query! conn :find-flyway-managed-schemas {:schema (str schema-prefix "%")})
                     (map :table_schema))
        reference-schema (->> schemas
                              (take 10) ; short-cut in case all tenant schemas are out of date
                              (filter db/tenant-schema-up-to-date?)
                              (first))]
    (when reference-schema
      (let [reference-history (simplify-schema-history (jdbc/query conn [(schema-history-query reference-schema)]))]
        (->> (jdbc/query conn [(->> schemas
                                    (map schema-history-query)
                                    (str/join " UNION ALL "))])
             (group-by :schema)
             (filter (fn [[_schema history]]
                       (= reference-history (simplify-schema-history history))))
             (keys)
             (doall))))))
