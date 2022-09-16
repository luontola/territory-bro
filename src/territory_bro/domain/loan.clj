;; Copyright © 2015-2022 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.loan
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str])
  (:import (java.net URL)
           (java.time Duration)))

(defn download [url]
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
      (slurp in))))

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
                      (select-keys [:number :loaned? :staleness])
                      (update :loaned? #(Boolean/parseBoolean %))
                      (update :staleness #(Long/parseLong %)))))))))
