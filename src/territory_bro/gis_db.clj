;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-db
  (:require [medley.core :refer [remove-vals]]
            [territory-bro.db :as db])
  (:import (java.util UUID)))

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

(defn- format-gis-change [change]
  change)

(defn get-changes
  ([conn]
   (get-changes conn {}))
  ([conn search]
   (->> (query! conn :get-gis-changes search)
        (map format-gis-change)
        (doall))))
