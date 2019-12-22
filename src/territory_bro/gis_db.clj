;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-db
  (:require [territory-bro.db :as db]))

(def ^:private query! (db/compile-queries "db/hugsql/gis.sql"))

(defn- format-gis-change [change]
  change)

(defn get-changes
  ([conn]
   (get-changes conn {}))
  ([conn search]
   (->> (query! conn :get-gis-changes search)
        (map format-gis-change)
        (doall))))
