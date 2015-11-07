(ns territory-bro.domain)

(defn geojson-to-territories [geojson]
  (when (not= "FeatureCollection" (get geojson "type"))
    (throw (IllegalArgumentException. (str "Not a FeatureCollection: " geojson))))
  (let [crs (get geojson "crs")]
    (map (fn [feature]
           {:name    (get-in feature ["properties" "name"])
            :address (get-in feature ["properties" "address"])
            :area    (assoc (get feature "geometry")
                       "crs" crs)})
         (get geojson "features"))))
