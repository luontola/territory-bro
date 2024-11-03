(ns territory-bro.ui.maps
  (:require [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]))

(def map-rasters
  (resources/auto-refresher "map-rasters.json" #(json/read-value (slurp %))))

(def default-for-availability "osm")
(def default-for-quality "osmhd")
