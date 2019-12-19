;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.presence-tracker
  (:require [territory-bro.util :refer [conj-set]]))

(defn inspect [m keyspace k]
  (let [{:keys [state desired actual]
         :or {desired :absent, actual :absent}} (get-in m [keyspace ::presence k])]
    {:state state
     :desired desired
     :actual actual
     :action (case [desired actual]
               [:present :absent] :create
               [:absent :present] :delete
               :ignore)}))

(defn creatable [m keyspace]
  (->> (get-in m [keyspace ::creatable])
       (map #(get-in m [keyspace ::presence % :state]))))

(defn deletable [m keyspace]
  (->> (get-in m [keyspace ::deletable])
       (map #(get-in m [keyspace ::presence % :state]))))


(defn merge-state [m keyspace k new-state]
  (update-in m [keyspace ::presence k :state] merge new-state))

(defn- update-indexes [m keyspace k]
  (let [action (:action (inspect m keyspace k))]
    (-> m
        (update-in [keyspace ::creatable] (if (= :create action) conj-set disj) k)
        (update-in [keyspace ::deletable] (if (= :delete action) conj-set disj) k))))

(defn- assert-presence [presence]
  (assert (or (= :present presence)
              (= :absent presence))
          {:presence presence}))

(defn set-desired [m keyspace k presence]
  (assert-presence presence)
  (-> m
      (assoc-in [keyspace ::presence k :desired] presence)
      (update-indexes keyspace k)))

(defn set-actual [m keyspace k presence]
  (assert-presence presence)
  (-> m
      (assoc-in [keyspace ::presence k :actual] presence)
      (update-indexes keyspace k)))
