(ns territory-bro.domain.export-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.domain.export :as export]
            [territory-bro.ui.html :as html])
  (:import (java.time LocalDate)
           (org.apache.poi.ss.usermodel Cell DataFormatter Row)
           (org.apache.poi.xssf.usermodel XSSFSheet XSSFWorkbook)))

(defn string-value [^Cell cell]
  (when cell
    (-> (DataFormatter.)
        (.formatCellValue cell))))

(defn visible-text [^XSSFSheet sheet]
  (->> (iterator-seq (.rowIterator sheet))
       (mapcat (fn [^Row row]
                 (iterator-seq (.cellIterator row))))
       (mapv string-value)
       (str/join " ")
       (html/normalize-whitespace)))

(deftest make-spreadsheet-test
  (testing "lists the territories an assignments on their own sheets"
    (let [territories [{:territory/number "123A",
                        :territory/region "West Side"
                        :territory/addresses "Address 1 A-C"
                        :territory/location territory-bro.domain.testdata/wkt-multi-polygon,
                        :territory/last-covered (LocalDate/of 2024 2 1)
                        :territory/do-not-calls "Address 1 B 42"}]
          assignments [{:territory/number "123A"
                        :publisher/name "Pete Publisher"
                        :assignment/start-date (LocalDate/of 2024 1 1)
                        :assignment/covered-dates #{(LocalDate/of 2024 2 1)}
                        :assignment/end-date (LocalDate/of 2024 3 1)}]
          wb (XSSFWorkbook. (export/make-spreadsheet {:territories territories
                                                      :assignments assignments}))]
      (is (= (html/normalize-whitespace
              "Number   Region      Addresses       Do-not-calls     Assigned?   Last covered   Territory boundaries
               123A     West Side   Address 1 A-C   Address 1 B 42   FALSE       2/1/24         MULTIPOLYGON(((30 20,45 40,10 40,30 20)),((15 5,40 10,10 20,5 10,15 5)))")
             (-> (.getSheet wb "Territories")
                 visible-text))
          "territories")
      (is (= (html/normalize-whitespace
              "Territory   Publisher        Assigned   Covered   Returned
               123A        Pete Publisher   1/1/24     2/1/24    3/1/24")
             (-> (.getSheet wb "Assignments")
                 visible-text))
          "assignments"))))
