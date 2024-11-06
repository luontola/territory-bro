(ns territory-bro.domain.publisher
  (:require [clojure.core.cache.wrapped :as cache]
            [clojure.string :as str]
            [mount.core :as mount]
            [territory-bro.infra.db :as db]
            [territory-bro.ui.html :as html])
  (:import (java.time Duration)
           (territory_bro ValidationException)))

(def ^:private queries (db/compile-queries "db/hugsql/publisher.sql"))

(defn- format-publisher [row]
  (when (some? row)
    {:congregation/id (:congregation row)
     :publisher/id (:id row)
     :publisher/name (:name row)}))

(defn- publishers-by-id*
  ([conn cong-id]
   (publishers-by-id* conn cong-id nil))
  ([conn cong-id {:keys [for-update?]}]
   (->> (db/query! conn queries :list-publishers {:congregation cong-id
                                                  :for-update? for-update?})
        (mapv format-publisher)
        (reduce (fn [m publisher]
                  (assoc m (:publisher/id publisher) publisher))
                {}))))

(mount/defstate publishers-cache
  :start (cache/ttl-cache-factory {:ttl (.toMillis (Duration/ofMinutes 5))}))

(defn ^:dynamic publishers-by-id [conn cong-id]
  (cache/lookup-or-miss publishers-cache cong-id (partial publishers-by-id* conn)))

(defn list-publishers [conn cong-id]
  (vals (publishers-by-id conn cong-id)))

(defn get-by-id [conn cong-id publisher-id]
  (-> (publishers-by-id conn cong-id)
      (get publisher-id)))

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
                             (vals (publishers-by-id* conn cong-id {:for-update? true})))]
    (when (str/blank? new-name)
      (throw (ValidationException. [[:missing-name]])))
    (when (contains? existing-names (str/lower-case new-name))
      (throw (ValidationException. [[:non-unique-name]])))
    (db/query! conn queries :save-publisher {:congregation cong-id
                                             :id publisher-id
                                             :name new-name})
    (cache/evict publishers-cache cong-id)
    nil))
