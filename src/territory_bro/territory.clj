;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.territory
  (:require [territory-bro.db :as db])
  (:import (java.util UUID)))

(def ^:private query! (db/compile-queries "db/hugsql/territory.sql"))

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

(defn get-by-id [conn id]
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
