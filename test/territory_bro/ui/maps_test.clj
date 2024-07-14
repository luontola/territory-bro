;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.maps-test
  (:require [clojure.test :refer :all]
            [territory-bro.ui.maps :as maps]))

(deftest default-map-rasters-test
  (testing "the default map raster IDs exist"
    (let [valid-ids (set (mapv :id (maps/map-rasters)))]
      (is (contains? valid-ids maps/default-for-availability))
      (is (contains? valid-ids maps/default-for-quality)))))
