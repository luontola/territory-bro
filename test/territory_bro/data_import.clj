(ns territory-bro.data-import
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.projections :as projections]
            [territory-bro.ui.html :as html])
  (:import (java.io PrintWriter)
           (java.time LocalDate)
           (org.apache.poi.ss.usermodel Cell CellType DataFormatter DateUtil Row Sheet)
           (org.apache.poi.util LocaleUtil)
           (org.apache.poi.xssf.usermodel XSSFWorkbook)))

(def cong-id (parse-uuid ""))
(def deleted-territories #{})
(def system (str (ns-name *ns*)))

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

(defn publisher-names [assignments]
  (->> assignments
       (map :publisher)
       distinct
       sort))

(defn import-publishers-commands [publisher-names]
  (mapv (fn [publisher-name]
          {:command/type :publisher.command/add-publisher
           :congregation/id cong-id
           :publisher/id (random-uuid)
           :publisher/name publisher-name})
        publisher-names))

(defn import-assignments-commands [assignments]
  (db/with-transaction [conn {:read-only true}]
    (let [state (projections/cached-state)
          publishers (publisher/list-publishers conn cong-id)
          publisher-name->id (-> (group-by :publisher/name publishers)
                                 (update-vals #(-> % first :publisher/id)))
          territories (vals (get-in state [:territory-bro.domain.territory/territories cong-id]))
          territory-number->id (-> (group-by :territory/number territories)
                                   (update-vals (fn [territories]
                                                  (when (= 1 (count territories))
                                                    (-> territories first :territory/id)))))]
      (->> assignments
           (remove #(contains? deleted-territories (:territory %)))
           (mapcat (fn [assignment]
                     (let [territory-id (-> assignment :territory territory-number->id)
                           assignment-id (random-uuid)
                           publisher-id (-> assignment :publisher publisher-name->id)]
                       (assert (some? territory-id) assignment)
                       (assert (some? publisher-id) assignment)
                       (try
                         [{:command/type :territory.command/assign-territory
                           :congregation/id cong-id
                           :territory/id territory-id
                           :assignment/id assignment-id
                           :date (-> assignment :start-date LocalDate/parse)
                           :publisher/id publisher-id}
                          (when (some? (:end-date assignment))
                            {:command/type :territory.command/return-territory
                             :congregation/id cong-id
                             :territory/id territory-id
                             :assignment/id assignment-id
                             :date (-> assignment :end-date LocalDate/parse)
                             :returning? true
                             :covered? true})]
                         (catch Exception e
                           (prn 'assignment assignment)
                           (throw e))))))
           (filterv some?)))))


(defn- enrich-command [command]
  (-> command
      (assoc :command/time (config/now))
      (assoc :command/system system)))

(defn dispatch-commands! [commands]
  (db/with-transaction [conn {:rollback-only true}] ; toggle after testing that all commands pass
    (let [state (projections/cached-state)]
      (doseq [command commands]
        (dispatcher/command! conn state (enrich-command command)))))
  (projections/refresh-async!))


(comment
  (->> (parse-excel "alueet.xlsx")
       #_(take 10)
       import-assignments-commands)
  (import-publishers-commands ["foo"])
  (->> (parse-excel "alueet.xlsx")
       publisher-names
       import-publishers-commands))
