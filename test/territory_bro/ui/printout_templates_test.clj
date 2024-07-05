;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printout-templates-test
  (:require [clojure.test :refer :all]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.ui.printout-templates :as printout-templates])
  (:import (java.time ZoneId)))

(deftest timezone-for-location-test
  (testing "Helsinki - positive timezone"
    (is (= (ZoneId/of "Europe/Helsinki")
           (printout-templates/timezone-for-location "MULTIPOLYGON(((24.941469073172712 60.17126251652484,24.94092595911725 60.17078337925611,24.942114661578245 60.17078337925611,24.941469073172712 60.17126251652484)))"))))

  (testing "London - near UTC, but uses daylight saving time"
    (is (= (ZoneId/of "Europe/London")
           (printout-templates/timezone-for-location "MULTIPOLYGON(((-0.092339529987027 51.51767499211157,-0.096080368276224 51.51400976107682,-0.087961953265625 51.51351443696474,-0.092339529987027 51.51767499211157)))"))))

  (testing "New York - negative timezone"
    (is (= (ZoneId/of "America/New_York")
           (printout-templates/timezone-for-location "MULTIPOLYGON(((-74.00664903771184 40.71882142880113,-74.01503824925818 40.70845374345747,-73.9960713361969 40.70859198988125,-74.00664903771184 40.71882142880113)))"))))

  (testing "Buenos Aires - negative latitude and longitude"
    (is (= (ZoneId/of "America/Argentina/Buenos_Aires")
           (printout-templates/timezone-for-location "MULTIPOLYGON(((-58.445267316446234 -34.604002458035154,-58.45017281675289 -34.60971073580903,-58.43545631583296 -34.610685280599476,-58.445267316446234 -34.604002458035154)))")))))
