(ns territory-bro.gis.qgis-test
  (:require [clojure.test :refer :all]
            [territory-bro.gis.qgis :as qgis]))

(deftest project-file-name-test
  (is (= "foo.qgs" (qgis/project-file-name "foo")))

  (testing "keeps non-ASCII characters"
    (is (= "Ylöjärvi.qgs" (qgis/project-file-name "Ylöjärvi")))
    (is (= "東京.qgs" (qgis/project-file-name "東京"))))

  (testing "strips illegal characters"
    (is (= "foobar.qgs" (qgis/project-file-name "foo<>:\"/\\|?*bar"))))

  (testing "normalizes whitespace"
    (is (= "foo bar gazonk.qgs" (qgis/project-file-name "foo    bar\n\tgazonk"))))

  (testing "default if name is empty"
    (is (= "territories.qgs" (qgis/project-file-name "")))
    (is (= "territories.qgs" (qgis/project-file-name "/")))))
