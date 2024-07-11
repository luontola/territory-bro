;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printout-templates-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.printout-templates :as printout-templates])
  (:import (java.time LocalDate)
           (java.util UUID)))

(use-fixtures :once (fn [f]
                      (binding [html/*page-path* "page-url"]
                        (f))))

(deftest print-date-notice-test
  (is (= "Printed 2000-12-31 with TerritoryBro.com [content]"
         (-> (printout-templates/print-date-notice (LocalDate/of 2000 12 31) "[content]")
             html/visible-text))))

(def territory-printout-model
  {:territory {:id (UUID. 0 1)
               :number "123"
               :addresses "The Addresses"
               :region "The Region"
               :location "MULTIPOLYGON(territory)"}
   :congregation-boundary "MULTIPOLYGON(congregation boundary)"
   :enclosing-region "MULTIPOLYGON(region)"
   :enclosing-minimap-viewport "POLYGON(minimap viewport)"
   :map-raster "osmhd"
   :print-date (LocalDate/of 2024 7 10)})

(def region-printout-model
  {:region {:name "The Region"
            :location "MULTIPOLYGON(region)"}
   :territories "[{\"number\":\"123\",\"location\":\"MULTIPOLYGON(territory)\"}]"
   :map-raster "osmhd"
   :print-date (LocalDate/of 2024 7 10)})

(deftest territory-card-test
  (testing "no data"
    (is (= (html/normalize-whitespace
            "Territory Map Card
             Printed with TerritoryBro.com
             Please keep this card in the envelope. Do not soil, mark or bend it.
             Each time the territory is covered, please inform the brother who cares for the territory files.")
           (-> (printout-templates/territory-card nil)
               html/visible-text))))

  (testing "full data"
    (let [html (printout-templates/territory-card territory-printout-model)]
      (is (= (html/normalize-whitespace
              "Territory Map Card
               The Region
               123
               Printed 2024-07-10 with TerritoryBro.com
               The Addresses
               Please keep this card in the envelope. Do not soil, mark or bend it.
               Each time the territory is covered, please inform the brother who cares for the territory files.")
             (html/visible-text html)))
      (is (str/includes? html "territory-location=\"MULTIPOLYGON(territory)\""))
      (is (str/includes? html "congregation-boundary=\"MULTIPOLYGON(congregation boundary)\""))
      (is (str/includes? html "enclosing-region=\"MULTIPOLYGON(region)\""))
      (is (str/includes? html "enclosing-minimap-viewport=\"POLYGON(minimap viewport)\""))
      (is (str/includes? html "map-raster=\"osmhd\""))
      (is (str/includes? html "hx-get=\"page-url/qr-code/00000000-0000-0000-0000-000000000001\"")))))

(deftest territory-card-map-only-test
  (testing "no data"
    (is (= (html/normalize-whitespace
            "Territory Map Card
             Printed with TerritoryBro.com
             Please keep this card in the envelope. Do not soil, mark or bend it.
             Each time the territory is covered, please inform the brother who cares for the territory files.")
           (-> (printout-templates/territory-card-map-only nil)
               html/visible-text))))

  (testing "full data"
    (let [html (printout-templates/territory-card-map-only territory-printout-model)]
      (is (= (html/normalize-whitespace
              "Territory Map Card
               The Region
               123
               Printed 2024-07-10 with TerritoryBro.com
               Please keep this card in the envelope. Do not soil, mark or bend it.
               Each time the territory is covered, please inform the brother who cares for the territory files.")
             (html/visible-text html)))
      (is (str/includes? html "territory-location=\"MULTIPOLYGON(territory)\""))
      (is (str/includes? html "congregation-boundary=\"MULTIPOLYGON(congregation boundary)\""))
      (is (str/includes? html "enclosing-region=\"MULTIPOLYGON(region)\""))
      (is (str/includes? html "enclosing-minimap-viewport=\"POLYGON(minimap viewport)\""))
      (is (str/includes? html "map-raster=\"osmhd\""))
      (is (str/includes? html "hx-get=\"page-url/qr-code/00000000-0000-0000-0000-000000000001\"")))))

(deftest rural-territory-card-test
  (testing "no data"
    (is (= (html/normalize-whitespace
            "Territory Map Card
             Printed with TerritoryBro.com")
           (-> (printout-templates/rural-territory-card nil)
               html/visible-text))))

  (testing "full data"
    (let [html (printout-templates/rural-territory-card territory-printout-model)]
      (is (= (html/normalize-whitespace
              "Territory Map Card
               The Region
               123
               Printed 2024-07-10 with TerritoryBro.com")
             (html/visible-text html)))
      (is (str/includes? html "territory-location=\"MULTIPOLYGON(territory)\""))
      (is (str/includes? html "congregation-boundary=\"MULTIPOLYGON(congregation boundary)\""))
      (is (str/includes? html "enclosing-region=\"MULTIPOLYGON(region)\""))
      (is (str/includes? html "enclosing-minimap-viewport=\"POLYGON(minimap viewport)\""))
      (is (str/includes? html "map-raster=\"osmhd\""))
      (is (str/includes? html "hx-get=\"page-url/qr-code/00000000-0000-0000-0000-000000000001\"")))))

(deftest neighborhood-card-test
  (testing "no data"
    (is (= "" (-> (printout-templates/neighborhood-card nil)
                  html/visible-text))))

  (testing "full data"
    (let [html (printout-templates/neighborhood-card territory-printout-model)]
      (is (= "" (html/visible-text html)))
      (is (str/includes? html "territory-number=\"123\""))
      (is (str/includes? html "territory-location=\"MULTIPOLYGON(territory)\""))
      (is (str/includes? html "map-raster=\"osmhd\"")))))

(deftest qr-code-only-test
  (testing "no data"
    (is (= "" (-> (printout-templates/qr-code-only nil)
                  html/visible-text))))

  (testing "full data"
    (let [html (printout-templates/qr-code-only territory-printout-model)]
      (is (= "123" (html/visible-text html)))
      (is (str/includes? html "hx-get=\"page-url/qr-code/00000000-0000-0000-0000-000000000001\"")))))

(deftest region-printout-test
  (testing "no data"
    (is (= "Printed with TerritoryBro.com"
           (-> (printout-templates/region-printout nil)
               html/visible-text))))

  (testing "full data"
    (let [html (printout-templates/region-printout region-printout-model)]
      (is (= (html/normalize-whitespace
              "The Region
               Printed 2024-07-10 with TerritoryBro.com")
             (html/visible-text html)))
      (is (str/includes? html "region-location=\"MULTIPOLYGON(region)\""))
      (is (str/includes? html "territories=\"[{&quot;number&quot;:&quot;123&quot;,&quot;location&quot;:&quot;MULTIPOLYGON(territory)&quot;}]\""))
      (is (str/includes? html "map-raster=\"osmhd\"")))))
