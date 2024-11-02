;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.publisher
  (:require [territory-bro.infra.db :as db])
  (:import (territory_bro ValidationException)))

(def ^:private queries (db/compile-queries "db/hugsql/publisher.sql"))

(defn- format-publisher [row]
  (when (some? row)
    {:congregation/id (:congregation row)
     :publisher/id (:id row)
     :publisher/name (:name row)}))

(defn ^:dynamic list-publishers [conn cong-id]
  (->> (db/query! conn queries :list-publishers {:congregation cong-id})
       (mapv format-publisher)))

(defn ^:dynamic get-by-id [conn cong-id publisher-id]
  (format-publisher (db/query! conn queries :get-publisher {:congregation cong-id
                                                            :id publisher-id})))

(defn check-publisher-exists [conn cong-id publisher-id]
  (when (nil? (get-by-id conn cong-id publisher-id))
    (throw (ValidationException. [[:no-such-publisher cong-id publisher-id]]))))

(defn save-publisher! [conn publisher]
  (db/query! conn queries :save-publisher {:congregation (:congregation/id publisher)
                                           :id (:publisher/id publisher)
                                           :name (:publisher/name publisher)}))
