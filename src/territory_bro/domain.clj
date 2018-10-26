; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain
  (:require [territory-bro.geojson :as geojson]))

(defn feature-to-territory [feature]
  {:number (get-in feature [:properties "number"])
   :address (get-in feature [:properties "address"])
   :region (get-in feature [:properties "region"])
   :location (get feature :geometry)})

(defn geojson-to-territories [geojson]
  (map feature-to-territory (geojson/explode-feature-collection geojson)))

(defn- parse-boolean [value]
  (or (= true value)
      (= "t" value)))

(defn feature-to-region [feature]
  {:name (get-in feature [:properties "name"])
   :minimap_viewport (parse-boolean (get-in feature [:properties "minimap_viewport"]))
   :congregation (parse-boolean (get-in feature [:properties "congregation"]))
   :subregion (parse-boolean (get-in feature [:properties "subregion"]))
   :location (get feature :geometry)})

(defn geojson-to-regions [geojson]
  (map feature-to-region (geojson/explode-feature-collection geojson)))
