(ns territory-bro.data-import
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.ui.html :as html])
  (:import (java.io PrintWriter)
           (org.apache.poi.ss.usermodel Cell CellType DataFormatter DateUtil Row Sheet)
           (org.apache.poi.util LocaleUtil)
           (org.apache.poi.xssf.usermodel XSSFWorkbook)))

(defn string-value [^Cell cell]
  (when cell
    (.formatCellValue (DataFormatter.) cell)))

(defn date-value [^Cell cell]
  (when (and cell
             (= CellType/NUMERIC (.getCellType cell))
             (DateUtil/isCellDateFormatted cell))
    (-> (.getDateCellValue cell)
        .toInstant
        (.atZone (.toZoneId (LocaleUtil/getUserTimeZone)))
        .toLocalDate
        str)))

(defn clean-territory-number [s]
  (-> s
      str
      html/normalize-whitespace
      (str/replace "Koodi" "")
      (str/replace "koodi" "")
      (str/replace "ovipuhelin" "")
      (str/replace "lukossa" "")
      (str/replace "!!!" "")
      (str/replace "(ei käytössä)" "")
      str/trim))

(defn clean-publisher-name [s]
  (-> s
      html/normalize-whitespace
      (str/replace #" (M|KK|K|R|Konv kut|konv kut|mj)$" "")
      (str/replace #" (M|KK|K|R|Konv kut|konv kut|mj)$" "")
      (str/replace #"/ryhmä$" "")))


(defn parse-excel [^String file]
  (let [workbook (XSSFWorkbook. file)
        *territory-number (atom nil)]
    (->> (for [^Sheet sheet (iterator-seq (.sheetIterator workbook))
               ^Row row sheet]
           (try
             (let [cell-A (.getCell row 0)
                   cell-B (.getCell row 1)
                   cell-C (.getCell row 2)
                   cell-E (.getCell row 4)]
               (when (zero? (.getRowNum row))
                 (reset! *territory-number nil)) ;; skip sheets which don't contain territories
               (cond
                 ;; read the territory number
                 (= "territory" (string-value cell-E))
                 (do
                   (reset! *territory-number (clean-territory-number (string-value cell-A)))
                   nil)

                 ;; skip header row
                 (= "Lainaaja" (string-value cell-A))
                 nil

                 ;; read assignment
                 (and (some? @*territory-number)
                      (not (str/blank? (string-value cell-A))))
                 {:territory @*territory-number
                  :publisher (clean-publisher-name (string-value cell-A))
                  :start-date (date-value cell-B)
                  :end-date (date-value cell-C)}))
             (catch Exception e
               (println (str "ERROR in sheet '" (.getSheetName sheet) "' row " (.getRowNum row)))
               (.printStackTrace e ^PrintWriter *out*)
               (throw e))))
         (filter some?))))

(defn generate-commands [assignment]
  []) ; TODO

(comment
  (pp/pprint)
  (->> (parse-excel "alueet.xlsx")
       (mapcat generate-commands))
  (->> (parse-excel "alueet.xlsx")
       (map :publisher)
       distinct
       sort))
