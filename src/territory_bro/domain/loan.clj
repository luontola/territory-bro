(ns territory-bro.domain.loan
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [medley.core :as m])
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
                  (try
                    {:territory/number (:number row)
                     :territory/loaned? (Boolean/parseBoolean (:loaned row))
                     :territory/staleness (Long/parseLong (:staleness row))}
                    (catch Exception e
                      (log/warn e "Failed to parse loans CSV row" (pr-str row))))))))))

(defn enrich-territory-loans! [territories loans-csv-url]
  (try
    (let [loans (-> (download! loans-csv-url)
                    (parse-loans-csv))
          number->loan (->> loans
                            (group-by :territory/number)
                            (m/map-vals first))]
      (mapv (fn [territory]
              (merge territory (number->loan (:territory/number territory))))
            territories))
    (catch Throwable t
      (log/error t "Failed to enrich territories with loans from" loans-csv-url)
      territories)))
