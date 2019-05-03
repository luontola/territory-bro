;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.region
  (:require [medley.core :refer [remove-vals]]
            [territory-bro.db :as db])
  (:import (java.util UUID)))

(def ^:private query! (db/compile-queries "db/hugsql/region.sql"))

(defn- format-region [territory]
  (remove-vals nil? {::id (:id territory)
                     ::name (:name territory)
                     ::location (:location territory)}))

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
