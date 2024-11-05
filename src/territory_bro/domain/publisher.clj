(ns territory-bro.domain.publisher
  (:require [clojure.string :as str]
            [territory-bro.infra.db :as db]
            [territory-bro.ui.html :as html])
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

(def ^:private normalized-name html/normalize-whitespace)

(defn publisher-name->id [publisher-name publishers]
  (let [needle (str/lower-case (normalized-name publisher-name))
        found (filterv #(= needle (str/lower-case (:publisher/name %)))
                       publishers)]
    (when (= 1 (count found))
      (-> found first :publisher/id))))

(defn save-publisher! [conn publisher]
  (let [cong-id (:congregation/id publisher)
        publisher-id (:publisher/id publisher)
        new-name (normalized-name (:publisher/name publisher))
        existing-names (into #{}
                             (comp (remove #(= publisher-id (:publisher/id %)))
                                   (map #(str/lower-case (:publisher/name %))))
                             (list-publishers conn cong-id))]
    (when (str/blank? new-name)
      (throw (ValidationException. [[:missing-name]])))
    (when (contains? existing-names (str/lower-case new-name))
      (throw (ValidationException. [[:non-unique-name]])))
    (db/query! conn queries :save-publisher {:congregation cong-id
                                             :id publisher-id
                                             :name new-name})))
