; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.geojson)

(defn explode-feature-collection [collection]
  (when (not= "FeatureCollection" (get collection "type"))
    (throw (IllegalArgumentException. (str "Not a FeatureCollection: " collection))))
  (map (fn [feature]
         {:properties (get feature "properties")
          :geometry (assoc (get feature "geometry")
                      "crs" (get collection "crs"))})
       (get collection "features")))
