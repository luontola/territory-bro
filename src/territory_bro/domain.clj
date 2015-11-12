(ns territory-bro.domain)

(defn geojson-to-territories [geojson]
  (when (not= "FeatureCollection" (get geojson "type"))
    (throw (IllegalArgumentException. (str "Not a FeatureCollection: " geojson))))
  (let [crs (get geojson "crs")]
    (map (fn [feature]
           {:number   (get-in feature ["properties" "number"])
            :address  (get-in feature ["properties" "address"])
            :region   (get-in feature ["properties" "region"])
            :location (assoc (get feature "geometry")
                        "crs" crs)})
         (get geojson "features"))))
