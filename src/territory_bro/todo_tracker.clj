;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.todo-tracker
  (:refer-clojure :exclude [get]))

(defn merge-state [m k new-state]
  (update-in m [::todo k ::state] merge new-state))

(defn- assert-presence [presence]
  (assert (or (= :present presence)
              (= :absent presence))
          {:presence presence}))

(defn set-desired [m k presence]
  (assert-presence presence)
  (assoc-in m [::todo k ::desired] presence))

(defn set-actual [m k presence]
  (assert-presence presence)
  (assoc-in m [::todo k ::actual] presence))

(defn get [m k]
  (let [{::keys [state desired actual]
         :or {desired :absent, actual :absent}} (get-in m [::todo k])]
    {:state state
     :desired desired
     :actual actual
     :action (case [desired actual]
               [:present :absent] :create
               [:absent :present] :delete
               :ignore)}))

(defn creatable [m]
  (->> (keys (::todo m))
       (map #(get m %))
       (filter #(= :create (:action %)))
       (map :state)))

(defn deletable [m]
  (->> (keys (::todo m))
       (map #(get m %))
       (filter #(= :delete (:action %)))
       (map :state)))
