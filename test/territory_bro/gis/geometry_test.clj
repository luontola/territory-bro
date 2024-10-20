;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.geometry-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.test.fixtures :refer :all])
  (:import (java.time ZoneId ZoneOffset)))

(deftest timezone-test
  (testing "defaults to UTC on error"
    (is (= ZoneOffset/UTC (geometry/timezone nil))
        "nil location")
    (is (= ZoneOffset/UTC (geometry/timezone (.createEmpty geometry/geometry-factory 2)))
        "empty location"))

  (testing "Helsinki - positive timezone"
    (is (= (ZoneId/of "Europe/Helsinki")
           (geometry/timezone (geometry/parse-wkt "MULTIPOLYGON(((24.941469073172712 60.17126251652484,24.94092595911725 60.17078337925611,24.942114661578245 60.17078337925611,24.941469073172712 60.17126251652484)))")))))

  (testing "London - near UTC, but uses daylight saving time"
    (is (= (ZoneId/of "Europe/London")
           (geometry/timezone (geometry/parse-wkt "MULTIPOLYGON(((-0.092339529987027 51.51767499211157,-0.096080368276224 51.51400976107682,-0.087961953265625 51.51351443696474,-0.092339529987027 51.51767499211157)))")))))

  (testing "New York - negative timezone"
    (is (= (ZoneId/of "America/New_York")
           (geometry/timezone (geometry/parse-wkt "MULTIPOLYGON(((-74.00664903771184 40.71882142880113,-74.01503824925818 40.70845374345747,-73.9960713361969 40.70859198988125,-74.00664903771184 40.71882142880113)))")))))

  (testing "Buenos Aires - negative latitude and longitude"
    (is (= (ZoneId/of "America/Argentina/Buenos_Aires")
           (geometry/timezone (geometry/parse-wkt "MULTIPOLYGON(((-58.445267316446234 -34.604002458035154,-58.45017281675289 -34.60971073580903,-58.43545631583296 -34.610685280599476,-58.445267316446234 -34.604002458035154)))"))))))

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

(deftest enclosing-tms-tile-test
  (let [geom (geometry/parse-wkt testdata/wkt-helsinki-rautatientori)
        tile (geometry/enclosing-tms-tile geom)]
    (is (= 17 (geometry/fit-tms-zoom-level geom)))
    (is (= {:x 37308, :y 18969, :zoom 16} tile))
    (is (= "https://tile.openstreetmap.org/16/37308/18969.png"
           (geometry/openstreetmap-tms-url tile)))))

(deftest union-test
  (let [area-1 (geometry/square [0 0] [10 10])
        area-2 (geometry/square [10 0] [20 10])
        area-1+2 (geometry/square [0 0] [20 10])]
    (is (nil? (geometry/union nil)))
    (is (nil? (geometry/union [])))
    (is (= area-1 (geometry/union [area-1])))
    (is (geometry/equals? area-1+2 (geometry/union [area-1 area-2])))))

(deftest fix-invalid-geometries
  (testing "fix TopologyException: found non-noded intersection between LINESTRING and LINESTRING"
    (let [bad-polygon "POLYGON((0 0, 1 1, 1 0, 0 1, 0 0)))" ; ▷◁ (intersects with itself)
          good-polygon "POLYGON((0 0, 1 0, 1 1, 0 1, 0 0)))"] ; □
      ;; Problem: https://github.com/locationtech/jts/issues/657
      ;; Solution: use GeometryFixer, https://github.com/locationtech/jts/issues/652
      (is (= "POLYGON ((1 1, 1 0, 0 0, 0 1, 1 1))"
             (->> [bad-polygon good-polygon]
                  (mapv geometry/parse-wkt)
                  (geometry/union)
                  (str))))
      (is (= "MULTIPOLYGON (((0.5 0.5, 1 1, 1 0, 0.5 0.5)), ((0 0, 0 1, 0.5 0.5, 0 0)))"
             (str (geometry/parse-wkt bad-polygon)))))))

(deftest equals?-test
  (let [area-1a (geometry/square [0 0] [1 1])
        area-1b (geometry/square [1 1] [0 0])
        area-2 (geometry/square [0 0] [1 2])]
    (is (not= area-1a area-1b))
    (is (true? (geometry/equals? area-1a area-1b)))
    (is (true? (geometry/equals? area-1b area-1a)))
    (is (false? (geometry/equals? area-1a area-2)))
    (is (false? (geometry/equals? area-2 area-1a)))
    (is (true? (geometry/equals? area-2 area-2)))))
