; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain :refer :all]))

(deftest test-geojson-to-territories
  (let [geojson {"type" "FeatureCollection",
                 "crs" {"type" "name",
                        "properties" {"name" "urn:ogc:def:crs:OGC:1.3:CRS84"}},
                 "features" [{"type" "Feature",
                              "properties" {"number" "330", "address" "Iiluodontie 1 A-C", "region" "Vuosaari"},
                              "geometry" {"type" "Polygon", "coordinates" [[[25.145782335183817 60.20474739390362] [25.146270369915616 60.20480723285933] [25.146904181255607 60.204092307671814]]]}}]}]

    (is (= {:number "330"
            :address "Iiluodontie 1 A-C"
            :region "Vuosaari"
            :location {"type" "Polygon"
                       "coordinates"
                       [[[25.145782335183817 60.20474739390362]
                         [25.146270369915616 60.20480723285933]
                         [25.146904181255607 60.204092307671814]]],
                       "crs" {"type" "name"
                              "properties" {"name" "urn:ogc:def:crs:OGC:1.3:CRS84"}}}}
           (first (geojson-to-territories geojson))))))

(deftest test-geojson-to-regions
  (let [expected-region {:name "Rastila"
                         :minimap_viewport false
                         :congregation false
                         :subregion true
                         :location {"type" "Polygon"
                                    "coordinates" [[[25.1224283 60.2180387]
                                                    [25.125388 60.217563]
                                                    [25.1278867 60.2156996]]]
                                    "crs" {"type" "name"
                                           "properties" {"name" "urn:ogc:def:crs:OGC:1.3:CRS84"}}}}]

    (testing "QGIS 2 format"
      (let [geojson {"type" "FeatureCollection",
                     "crs" {"type" "name",
                            "properties" {"name" "urn:ogc:def:crs:OGC:1.3:CRS84"}},
                     "features" [{"type" "Feature",
                                  "properties" {"id" 5, "name" "Rastila", "minimap_viewport" "f", "congregation" "f", "subregion" "t"},
                                  "geometry" {"type" "Polygon", "coordinates" [[[25.1224283, 60.2180387], [25.125388, 60.217563], [25.1278867, 60.2156996]]]}}]}]
        (is (= expected-region (first (geojson-to-regions geojson))))))

    (testing "QGIS 3 format"
      (let [geojson {"type" "FeatureCollection",
                     "name" "test regions",
                     "crs" {"type" "name",
                            "properties" {"name" "urn:ogc:def:crs:OGC:1.3:CRS84"}},
                     "features" [{"type" "Feature",
                                  "properties" {"id" 5, "name" "Rastila", "minimap_viewport" false, "congregation" false, "subregion" true},
                                  "geometry" {"type" "Polygon", "coordinates" [[[25.1224283 60.2180387] [25.125388 60.217563] [25.1278867 60.2156996]]]}}]}]
        (is (= expected-region (first (geojson-to-regions geojson))))))))
