;; Copyright © 2015-2022 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.loan
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [medley.core :refer [map-vals]])
  (:import (java.net URL)
           (java.time Duration)))

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
          header (map #(keyword (str/lower-case %))
                      header)
          rows (map zipmap
                    (repeat header)
                    rows)]
      (->> rows
           (remove #(str/blank? (:number %)))
           (map (fn [row]
                  {:territory/number (:number row)
                   :territory/loaned? (Boolean/parseBoolean (:loaned row))
                   :territory/staleness (Long/parseLong (:staleness row))}))))))

(defn enrich-territory-loans! [congregation]
  (try
    (let [loans (-> (download! (:congregation/loans-csv-url congregation))
                    (parse-loans-csv))
          number->loan (->> loans
                            (group-by :territory/number)
                            (map-vals first))
          with-loan (fn [territory]
                      (merge territory (number->loan (:territory/number territory))))]
      (update congregation :congregation/territories #(map with-loan %)))
    (catch Throwable t
      (log/error t "Failed to enrich congregation with territory loans" (:congregation/id congregation))
      congregation)))
