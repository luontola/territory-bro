;; Copyright © 2015-2022 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.loan
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [medley.core :refer [map-vals]])
  (:import (java.net URL)
           (java.time Duration)
           (java.util UUID)))

(defn ^:dynamic download! [url]
  (when url
    (let [url (URL. url)
          _ (when-not (= "https" (.getProtocol url))
              (throw (IllegalArgumentException. (str "Disallowed protocol: " url))))
          _ (when-not (= "docs.google.com" (.getHost url))
              (throw (IllegalArgumentException. (str "Disallowed host: " url))))
          conn (.openConnection url)
          timeout (.toMillis (Duration/ofSeconds 15))]
      (.setReadTimeout conn timeout)
      (.setConnectTimeout conn timeout)
      (with-open [in (.getInputStream conn)]
        (slurp in)))))

(defn parse-loans-csv [csv-string]
  (when csv-string
    (let [[header & rows] (csv/read-csv csv-string)
          header (->> header
                      (map #(keyword (str/lower-case %)))
                      (map #(case %
                              :loaned :loaned?
                              %)))
          rows (map zipmap
                    (repeat header)
                    rows)]
      (->> rows
           (remove #(str/blank? (:number %)))
           (map (fn [row]
                  (-> row
                      ;; TODO: convert to namespaced keys
                      (select-keys [:number :loaned? :staleness])
                      (update :loaned? #(Boolean/parseBoolean %))
                      (update :staleness #(Long/parseLong %)))))))))

(defn enrich-territory-loans! [congregation]
  ;; TODO: allow configuring the google sheets url (and invalidate this test url)
  (try
    (let [loans-url (when (= (UUID/fromString "778fdfae-d023-4475-a8ef-9dbb6ae8e350")
                             (:congregation/id congregation))
                      "https://docs.google.com/spreadsheets/d/e/2PACX-1vQ_onetqOuxwTZKOKOioeUtzhz0i_kFHQFDx-Tg2oMBt58Q4TlIfVPKh4zLY57l0Z3Gce-Ja2ePOAqk/pub?gid=1753802949&single=true&output=csv")
          loans (-> (download! loans-url)
                    (parse-loans-csv))
          number->loan (->> loans
                            (group-by :number)
                            (map-vals first))
          add-loan (fn [territory]
                     (if-some [loan (number->loan (:territory/number territory))]
                       (-> territory
                           (assoc :territory/loaned? (:loaned? loan))
                           (assoc :territory/staleness (:staleness loan)))
                       territory))]
      (update congregation :congregation/territories #(map add-loan %)))
    (catch Throwable t
      (log/error t "Failed to enrich congregation with territory loans" (:congregation/id congregation))
      congregation)))
