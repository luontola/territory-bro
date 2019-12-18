;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.todo-tracker
  (:refer-clojure :exclude [get]))

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
  (->> (::creatable m)
       (map #(get-in m [::todo % ::state]))))

(defn deletable [m]
  (->> (::deletable m)
       (map #(get-in m [::todo % ::state]))))


(defn merge-state [m k new-state]
  (update-in m [::todo k ::state] merge new-state))

(def ^:private conj-set (fnil conj #{}))

(defn- update-indexes [m k]
  (let [action (:action (get m k))]
    (-> m
        (update ::creatable (if (= :create action) conj-set disj) k)
        (update ::deletable (if (= :delete action) conj-set disj) k))))

(defn- assert-presence [presence]
  (assert (or (= :present presence)
              (= :absent presence))
          {:presence presence}))

(defn set-desired [m k presence]
  (assert-presence presence)
  (-> m
      (assoc-in [::todo k ::desired] presence)
      (update-indexes k)))

(defn set-actual [m k presence]
  (assert-presence presence)
  (-> m
      (assoc-in [::todo k ::actual] presence)
      (update-indexes k)))
