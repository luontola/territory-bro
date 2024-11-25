(ns territory-bro.ui.export-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.ui.export :as export]
            [territory-bro.ui.html :as html])
  (:import (clojure.lang ExceptionInfo)
           (java.io InputStream)
           (java.time LocalDate)
           (org.apache.poi.ss.usermodel Cell DataFormatter Row Sheet)
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

(deftest export-territories-test
  (with-fixtures [dmz-test/testdata-fixture]
    (testutil/with-user-id dmz-test/user-id

      (testing "generates an Excel spreadsheet with territories and assignments"
        (let [{:keys [content filename]} (export/export-territories dmz-test/cong-id)
              wb (XSSFWorkbook. ^InputStream content)
              territories-sheet (-> (.getSheet wb "Territories")
                                    visible-text)
              assignments-sheet (-> (.getSheet wb "Assignments")
                                    visible-text)]
          (is (= "Cong1 Name.xlsx" filename))
          (is (= ["Territories" "Assignments"]
                 (->> (iterator-seq (.sheetIterator wb))
                      (mapv Sheet/.getSheetName))))
          (is (str/includes? territories-sheet (html/normalize-whitespace "123   the region   the addresses   the do-not-calls"))
              "enriches territories with do-not-calls")
          (is (str/includes? assignments-sheet (html/normalize-whitespace "123   John Doe   1/1/00"))
              "enriches territories with assignment history")))

      (testing "requires the configure-congregation permission"
        (testutil/with-events [{:event/type :congregation.event/permission-revoked
                                :congregation/id dmz-test/cong-id
                                :user/id dmz-test/user-id
                                :permission/id :configure-congregation}]
          (is (thrown-match? ExceptionInfo dmz-test/access-denied
                             (export/export-territories dmz-test/cong-id))))))))
