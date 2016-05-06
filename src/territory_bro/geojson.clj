(ns territory-bro.geojson)

(defn explode-feature-collection [collection]
  (when (not= "FeatureCollection" (get collection "type"))
    (throw (IllegalArgumentException. (str "Not a FeatureCollection: " collection))))
  (map (fn [feature]
         {:properties (get feature "properties")
          :geometry   (assoc (get feature "geometry")
                        "crs" (get collection "crs"))})
       (get collection "features")))
