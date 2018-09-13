; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.geojson-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [territory-bro.geojson :refer :all]))

(deftest test-explode-feature-collection
  (let [geojson {"type" "FeatureCollection",
                 "crs" {"type" "name",
                        "properties" {"name" "urn:ogc:def:crs:OGC:1.3:CRS84"}},
                 "features" [{"type" "Feature",
                              "properties" {"foo" "a"
                                            "bar" "b"},
                              "geometry" {"type" "Polygon"
                                          "coordinates" [[[25.1 60.1] [25.2 60.2] [25.3 60.0]]]}}
                             {"type" "Feature",
                              "properties" {"foo" "c"
                                            "bar" "d"},
                              "geometry" {"type" "Polygon"
                                          "coordinates" [[[25.1 60.1] [25.2 60.2] [25.3 60.0]]]}}]}
        features (explode-feature-collection geojson)]

    (testing "returns seq of features in the collection"
      (is (seq? features))
      (is (= 2 (count features))))

    (testing "feature fields"
      (let [feature (first features)]
        (is (= {"foo" "a"
                "bar" "b"}
               (:properties feature)))
        (is (= {"type" "Polygon"
                "coordinates" [[[25.1 60.1] [25.2 60.2] [25.3 60.0]]]
                "crs" {"type" "name",
                       "properties" {"name" "urn:ogc:def:crs:OGC:1.3:CRS84"}}}
               (:geometry feature)))))

    (testing "requires FeatureCollection"
      (is (thrown? IllegalArgumentException
                   (explode-feature-collection (assoc geojson "type" "WrongType")))))))
