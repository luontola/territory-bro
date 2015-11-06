(ns territory-bro.domain-test
  (:require [territory-bro.domain :refer :all]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]))

(deftest test-geojson-to-territories
  (let [geojson {"type"     "FeatureCollection",
                 "crs"      {"type"       "name",
                             "properties" {"name" "urn:ogc:def:crs:OGC:1.3:CRS84"}},
                 "features" [{"type"       "Feature",
                              "properties" {"id" nil, "number" "330", "address" "Iiluodontie 1 A-C"},
                              "geometry"   {"type" "Polygon", "coordinates" [[[25.145782335183817 60.20474739390362] [25.146270369915616 60.20480723285933] [25.146904181255607 60.204092307671814] [25.146149945761014 60.20400097249475] [25.145782335183817 60.20474739390362]]]}}
                             {"type"       "Feature",
                              "properties" {"id" nil, "number" "304", "address" "Iiluodontie 3"},
                              "geometry"   {"type" "Polygon", "coordinates" [[[25.14611191708061 60.20499619726613] [25.14673939030721 60.20511272477442] [25.146986576729812 60.20481038227502] [25.146365441616616 60.204700152545676] [25.14611191708061 60.20499619726613]]]}}]}
        territories (geojson-to-territories geojson)]

    (testing "as many territories as features"
      (is (seq? territories))
      (is (= 2 (count territories))))

    (testing "territory fields"
      (let [territory (first territories)]
        (is (= "330" (:name territory)))
        (is (= "Iiluodontie 1 A-C" (:address territory)))
        (is (= "Polygon" (get-in territory [:area "type"])))
        (is (get-in territory [:area "coordinates"]))
        (is (get-in territory [:area "crs"]))))

    (testing "requires FeatureCollection"
      (is (thrown? IllegalArgumentException
                   (geojson-to-territories (assoc geojson "type" "WrongType")))))))
