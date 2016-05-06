(ns territory-bro.domain
  (:require [territory-bro.geojson :as geojson]))

(defn feature-to-territory [feature]
  {:number   (get-in feature [:properties "number"])
   :address  (get-in feature [:properties "address"])
   :region   (get-in feature [:properties "region"])
   :location (get feature :geometry)})

(defn geojson-to-territories [geojson]
  (map feature-to-territory (geojson/explode-feature-collection geojson)))

(defn feature-to-region [feature]
  {:name             (get-in feature [:properties "name"])
   :minimap_viewport (= "t" (get-in feature [:properties "minimap_viewport"]))
   :congregation     (= "t" (get-in feature [:properties "congregation"]))
   :subregion        (= "t" (get-in feature [:properties "subregion"]))
   :location         (get feature :geometry)})

(defn geojson-to-regions [geojson]
  (map feature-to-region (geojson/explode-feature-collection geojson)))
