(ns territory-bro.domain.territory
  (:require [clojure.string :as str]
            [medley.core :refer [dissoc-in greatest]]
            [territory-bro.gis.gis-change :as gis-change]
            [territory-bro.infra.util :refer [assoc-dissoc conj-set]])
  (:import (java.time LocalDate)
           (territory_bro ValidationException)))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defmethod projection :territory.event/territory-defined
  [state event]
  (update-in state [::territories (:congregation/id event) (:territory/id event)]
             (fn [territory]
               (-> territory
                   (assoc :territory/id (:territory/id event))
                   (assoc :territory/number (str/trim (:territory/number event)))
                   (assoc :territory/addresses (str/trim (:territory/addresses event)))
                   (assoc :territory/region (str/trim (:territory/region event)))
                   (assoc :territory/meta (:territory/meta event))
                   (assoc :territory/location (:territory/location event))))))

(defmethod projection :territory.event/territory-deleted
  [state event]
  (dissoc-in state [::territories (:congregation/id event) (:territory/id event)]))

(defn- update-assignment-status [territory]
  (let [assignments (vals (:territory/assignments territory))]
    (-> territory
        (assoc-dissoc :territory/current-assignment (->> assignments
                                                         (remove :assignment/end-date)
                                                         (first)))
        (assoc-dissoc :territory/last-covered (->> assignments
                                                   (mapcat :assignment/covered-dates)
                                                   (apply greatest))))))

(defmethod projection :territory.event/territory-assigned
  [state event]
  (update-in state [::territories (:congregation/id event) (:territory/id event)]
             (fn [territory]
               (-> territory
                   (assoc-in [:territory/assignments (:assignment/id event)] (select-keys event [:assignment/id :assignment/start-date :publisher/id]))
                   (update-assignment-status)))))

(defmethod projection :territory.event/territory-covered
  [state event]
  (update-in state [::territories (:congregation/id event) (:territory/id event)]
             (fn [territory]
               (-> territory
                   (update-in [:territory/assignments (:assignment/id event) :assignment/covered-dates] conj-set (:assignment/covered-date event))
                   (update-assignment-status)))))

(defmethod projection :territory.event/territory-returned
  [state event]
  (update-in state [::territories (:congregation/id event) (:territory/id event)]
             (fn [territory]
               (-> territory
                   (assoc-in [:territory/assignments (:assignment/id event) :assignment/end-date] (:assignment/end-date event))
                   (update-assignment-status)))))


;;;; Queries

(defn check-territory-exists [state cong-id territory-id]
  (when (nil? (get-in state [::territories cong-id territory-id]))
    (throw (ValidationException. [[:no-such-territory cong-id territory-id]]))))

(defn get-unrestricted-territory [state cong-id territory-id]
  (when-some [territory (get-in state [::territories cong-id territory-id])]
    (assoc territory :congregation/id cong-id)))


;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::territories (:congregation/id command) (:territory/id command)])))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _territory _injections]
                                      (:command/type command)))

(def ^:private data-keys
  [:territory/number
   :territory/addresses
   :territory/region
   :territory/meta
   :territory/location])

(defmethod command-handler :territory.command/define-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)
        old-data (select-keys territory data-keys)
        new-data (select-keys command data-keys)]
    (check-permit [:define-territory cong-id territory-id])
    (when (not= old-data new-data)
      [(merge {:event/type :territory.event/territory-defined
               :congregation/id cong-id
               :territory/id territory-id}
              (gis-change/event-metadata command)
              new-data)])))

(defmethod command-handler :territory.command/delete-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)]
    (check-permit [:delete-territory cong-id territory-id])
    (when (some? territory)
      [(merge {:event/type :territory.event/territory-deleted
               :congregation/id cong-id
               :territory/id territory-id}
              (gis-change/event-metadata command))])))

(defmethod command-handler :territory.command/assign-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)
        assignment-id (:assignment/id command)
        start-date (:date command)
        publisher-id (:publisher/id command)
        current-assignment-id (:assignment/id (:territory/current-assignment territory))
        assignments (:territory/assignments territory)]
    (check-permit [:assign-territory cong-id territory-id publisher-id])
    (when (and (some? current-assignment-id)
               (not= assignment-id current-assignment-id))
      (throw (ValidationException. [[:already-assigned cong-id territory-id]])))
    (when-not (contains? assignments assignment-id)
      [{:event/type :territory.event/territory-assigned
        :congregation/id cong-id
        :territory/id territory-id
        :assignment/id assignment-id
        :assignment/start-date start-date
        :publisher/id publisher-id}])))

(defmethod command-handler :territory.command/return-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)
        assignment-id (:assignment/id command)
        end-date ^LocalDate (:date command)
        assignment (get-in territory [:territory/assignments assignment-id])]
    (when (nil? assignment)
      (throw (ValidationException. [[:no-such-assignment cong-id territory-id assignment-id]])))
    (check-permit [:assign-territory cong-id territory-id (:publisher/id assignment)])
    (doseq [^LocalDate date (conj (:assignment/covered-dates assignment)
                                  (:assignment/start-date assignment))]
      (when (. end-date isBefore date)
        (throw (ValidationException. [[:invalid-end-date]]))))
    (when (nil? (:assignment/end-date assignment))
      (->> [(when (and (:covered? command)
                       (not (contains? (:assignment/covered-dates assignment) end-date)))
              {:event/type :territory.event/territory-covered
               :congregation/id cong-id
               :territory/id territory-id
               :assignment/id assignment-id
               :assignment/covered-date end-date})
            (when (:returning? command)
              {:event/type :territory.event/territory-returned
               :congregation/id cong-id
               :territory/id territory-id
               :assignment/id assignment-id
               :assignment/end-date end-date})]
           (filterv some?)))))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
