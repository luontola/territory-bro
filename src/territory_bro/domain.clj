(ns territory-bro.domain
  (:require [territory-bro.geojson :as geojson]))

(defn feature-to-territory [feature]
  {:number   (get-in feature [:properties "number"])
   :address  (get-in feature [:properties "address"])
   :region   (get-in feature [:properties "region"])
   :location (get feature :geometry)})

(defn geojson-to-territories [geojson]
  (map feature-to-territory (geojson/explode-feature-collection geojson)))
