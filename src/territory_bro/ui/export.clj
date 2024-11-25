(ns territory-bro.ui.export
  (:require [territory-bro.domain.dmz :as dmz]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.time LocalDate)
           (org.apache.poi.ss.usermodel BuiltinFormats VerticalAlignment)
           (org.apache.poi.xssf.usermodel XSSFSheet XSSFWorkbook)))

(def excel-spreadsheet-ext ".xlsx")

(defn- set-date-column-min-width [^XSSFSheet sheet column]
  ;; auto-size doesn't make the cells wide enough to fit all dates, likely depending on locale
  (.setColumnWidth sheet column (max 3000 (.getColumnWidth sheet column))))

(defn make-spreadsheet ^ByteArrayInputStream [{:keys [territories assignments]}]
  (let [wb (XSSFWorkbook.)
        bold-font (doto (.createFont wb)
                    (.setBold true))
        header-style (doto (.createCellStyle wb)
                       (.setVerticalAlignment VerticalAlignment/TOP)
                       (.setFont bold-font))
        text-style (doto (.createCellStyle wb)
                     (.setVerticalAlignment VerticalAlignment/TOP))
        wrapped-text-style (doto (.createCellStyle wb)
                             (.setVerticalAlignment VerticalAlignment/TOP)
                             (.setWrapText true))
        date-style (doto (.createCellStyle wb)
                     ;; Excel will show this short date format according to the operating system's regional date and time settings
                     (.setDataFormat (BuiltinFormats/getBuiltinFormat "m/d/yy"))
                     (.setVerticalAlignment VerticalAlignment/TOP))
        territories-sheet (.createSheet wb "Territories") ; TODO: i18n
        assignments-sheet (.createSheet wb "Assignments")] ; TODO: i18n

    ;; 100% zoom is too small for my screen, and Excel doesn't have a global default setting for it
    (.setZoom territories-sheet 150)
    (.setZoom assignments-sheet 150)

    ;;; Territories

    (let [row (.createRow territories-sheet 0)]
      (doto (.createCell row 0)
        (.setCellValue (i18n/t "Territory.number"))
        (.setCellStyle header-style))
      (doto (.createCell row 1)
        (.setCellValue (i18n/t "Territory.region"))
        (.setCellStyle header-style))
      (doto (.createCell row 2)
        (.setCellValue (i18n/t "Territory.addresses"))
        (.setCellStyle header-style))
      (doto (.createCell row 3)
        (.setCellValue (html/visible-text (i18n/t "Territory.doNotCalls")))
        (.setCellStyle header-style))
      (doto (.createCell row 4)
        (.setCellValue "Assigned?") ; TODO: i18n
        (.setCellStyle header-style))
      (doto (.createCell row 5)
        (.setCellValue (i18n/t "Assignment.lastCovered"))
        (.setCellStyle header-style))
      (doto (.createCell row 6)
        (.setCellValue "Territory boundaries") ; TODO: i18n
        (.setCellStyle header-style)))

    (.autoSizeColumn territories-sheet 6) ; only fit the header, not the WKT location data

    (doseq [territory territories]
      (let [row (.createRow territories-sheet (inc (.getLastRowNum territories-sheet)))]
        (doto (.createCell row 0)
          (.setCellValue (str (:territory/number territory)))
          (.setCellStyle text-style))
        (doto (.createCell row 1)
          (.setCellValue (str (:territory/region territory)))
          (.setCellStyle text-style))
        (doto (.createCell row 2)
          (.setCellValue (str (:territory/addresses territory)))
          (.setCellStyle wrapped-text-style))
        (doto (.createCell row 3)
          (.setCellValue (str (:territory/do-not-calls territory)))
          (.setCellStyle wrapped-text-style))
        (doto (.createCell row 4)
          (.setCellValue (some? (:territory/current-assignment territory)))
          (.setCellStyle text-style))
        (doto (.createCell row 5)
          (.setCellValue ^LocalDate (:territory/last-covered territory))
          (.setCellStyle date-style))
        (doto (.createCell row 6)
          (.setCellValue (str (:territory/location territory)))
          (.setCellStyle text-style))))

    (.autoSizeColumn territories-sheet 0)
    (.autoSizeColumn territories-sheet 1)
    (.autoSizeColumn territories-sheet 2)
    (.autoSizeColumn territories-sheet 3)
    (.autoSizeColumn territories-sheet 4)
    (.autoSizeColumn territories-sheet 5)
    (set-date-column-min-width territories-sheet 5)

    ;;; Assignments

    (let [row (.createRow assignments-sheet 0)]
      (doto (.createCell row 0)
        (.setCellValue "Territory") ; TODO: i18n
        (.setCellStyle header-style))
      (doto (.createCell row 1)
        (.setCellValue "Publisher") ; TODO: i18n
        (.setCellStyle header-style))
      (doto (.createCell row 2)
        (.setCellValue "Assigned") ; TODO: i18n
        (.setCellStyle header-style))
      (doto (.createCell row 3)
        (.setCellValue "Covered") ; TODO: i18n
        (.setCellStyle header-style))
      (doto (.createCell row 4)
        (.setCellValue "Returned") ; TODO: i18n
        (.setCellStyle header-style)))

    (doseq [assignment (->> assignments
                            (mapcat (fn [assignment]
                                      (->> (:assignment/covered-dates assignment)
                                           (mapv (fn [covered-date]
                                                   (-> assignment
                                                       (dissoc :assignment/covered-dates)
                                                       (assoc :assignment/covered-date covered-date))))
                                           (sort-by :assignment/covered-date)))))]
      (let [row (.createRow assignments-sheet (inc (.getLastRowNum assignments-sheet)))]
        (doto (.createCell row 0)
          (.setCellValue (str (:territory/number assignment)))
          (.setCellStyle text-style))
        (doto (.createCell row 1)
          (.setCellValue (str (:publisher/name assignment)))
          (.setCellStyle text-style))
        (doto (.createCell row 2)
          (.setCellValue ^LocalDate (:assignment/start-date assignment))
          (.setCellStyle date-style))
        (doto (.createCell row 3)
          (.setCellValue ^LocalDate (:assignment/covered-date assignment))
          (.setCellStyle date-style))
        (doto (.createCell row 4)
          (.setCellValue ^LocalDate (:assignment/end-date assignment))
          (.setCellStyle date-style))))

    (.autoSizeColumn assignments-sheet 0)
    (.autoSizeColumn assignments-sheet 1)
    (.autoSizeColumn assignments-sheet 2)
    (set-date-column-min-width assignments-sheet 2)
    (.autoSizeColumn assignments-sheet 3)
    (set-date-column-min-width assignments-sheet 3)
    (.autoSizeColumn assignments-sheet 4)
    (set-date-column-min-width assignments-sheet 4)

    (with-open [out (ByteArrayOutputStream.)]
      (.write wb out)
      (ByteArrayInputStream. (.toByteArray out)))))

(defn export-territories [cong-id]
  (when-not (dmz/allowed? [:configure-congregation cong-id])
    (dmz/access-denied!))
  (let [territories (->> (dmz/list-territories cong-id)
                         (mapv (fn [territory]
                                 (assoc territory :territory/do-not-calls (dmz/get-do-not-calls (:congregation/id territory) (:territory/id territory))))))
        assignments (->> territories
                         (mapcat (fn [territory]
                                   (->> (dmz/get-territory-assignment-history (:congregation/id territory) (:territory/id territory))
                                        (mapv #(assoc % :territory/number (:territory/number territory)))))))]
    (make-spreadsheet {:territories territories
                       :assignments assignments})))
