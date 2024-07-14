;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.maps
  (:require [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]))

(def map-rasters
  (resources/auto-refresher "map-rasters.json" #(json/read-value (slurp %))))

(def default-for-availability "osm")
(def default-for-quality "osmhd")
