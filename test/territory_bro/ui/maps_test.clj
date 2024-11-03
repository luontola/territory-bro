(ns territory-bro.ui.maps-test
  (:require [clojure.test :refer :all]
            [territory-bro.ui.maps :as maps]))

(deftest default-map-rasters-test
  (testing "the default map raster IDs exist"
    (let [valid-ids (set (mapv :id (maps/map-rasters)))]
      (is (contains? valid-ids maps/default-for-availability))
      (is (contains? valid-ids maps/default-for-quality)))))
