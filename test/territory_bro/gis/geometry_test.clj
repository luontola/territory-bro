;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.geometry-test
  (:require [clojure.test :refer :all]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.test.fixtures :refer :all])
  (:import (java.time ZoneId ZoneOffset)))

(deftest timezone-for-location-test
  (testing "invalid or missing location"
    (is (= ZoneOffset/UTC
           (geometry/timezone-for-location nil)
           (geometry/timezone-for-location "")
           (geometry/timezone-for-location "foo"))))

  (testing "Helsinki - positive timezone"
    (is (= (ZoneId/of "Europe/Helsinki")
           (geometry/timezone-for-location "MULTIPOLYGON(((24.941469073172712 60.17126251652484,24.94092595911725 60.17078337925611,24.942114661578245 60.17078337925611,24.941469073172712 60.17126251652484)))"))))

  (testing "London - near UTC, but uses daylight saving time"
    (is (= (ZoneId/of "Europe/London")
           (geometry/timezone-for-location "MULTIPOLYGON(((-0.092339529987027 51.51767499211157,-0.096080368276224 51.51400976107682,-0.087961953265625 51.51351443696474,-0.092339529987027 51.51767499211157)))"))))

  (testing "New York - negative timezone"
    (is (= (ZoneId/of "America/New_York")
           (geometry/timezone-for-location "MULTIPOLYGON(((-74.00664903771184 40.71882142880113,-74.01503824925818 40.70845374345747,-73.9960713361969 40.70859198988125,-74.00664903771184 40.71882142880113)))"))))

  (testing "Buenos Aires - negative latitude and longitude"
    (is (= (ZoneId/of "America/Argentina/Buenos_Aires")
           (geometry/timezone-for-location "MULTIPOLYGON(((-58.445267316446234 -34.604002458035154,-58.45017281675289 -34.60971073580903,-58.43545631583296 -34.610685280599476,-58.445267316446234 -34.604002458035154)))")))))

(deftest find-enclosing-test
  (let [area-1 (geometry/square [0 0] [10 10])
        area-2 (geometry/square [10 0] [20 10])
        big-area-1 (geometry/square [0 0] [20 20])
        enclosed-by-1 (geometry/square [5 5] [6 6])
        enclosed-by-2 (geometry/square [15 5] [16 6])
        overlaps-more-by-1 (geometry/square [8 5] [11 6])]

    (testing "returns nil when nothing was found"
      (is (nil? (geometry/find-enclosing enclosed-by-1 nil)))
      (is (nil? (geometry/find-enclosing enclosed-by-1 [])))
      (is (nil? (geometry/find-enclosing enclosed-by-1 [area-2]))))

    (testing "returns the enclosing area when there is only one option"
      (let [areas (shuffle [area-1 area-2])]
        (is (= area-1 (geometry/find-enclosing enclosed-by-1 areas)))
        (is (= area-2 (geometry/find-enclosing enclosed-by-2 areas)))))

    (testing "when overlaps multiple areas, returns the one which overlaps more"
      (is (= area-1 (geometry/find-enclosing overlaps-more-by-1 (shuffle [area-1 area-2])))))

    (testing "when overlaps multiple areas, returns the smallest enclosing area"
      (is (= area-1 (geometry/find-enclosing enclosed-by-1 (shuffle [area-1 big-area-1])))))))
