;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.territory
  (:require [medley.core :refer [dissoc-in]]
            [territory-bro.gis.gis-change :as gis-change]
            [territory-bro.infra.util :refer [conj-set]])
  (:import (territory_bro ValidationException)))

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
                   (assoc :territory/number (:territory/number event))
                   (assoc :territory/addresses (:territory/addresses event))
                   (assoc :territory/region (:territory/region event))
                   (assoc :territory/meta (:territory/meta event))
                   (assoc :territory/location (:territory/location event))))))

(defmethod projection :territory.event/territory-deleted
  [state event]
  (dissoc-in state [::territories (:congregation/id event) (:territory/id event)]))

(defmethod projection :territory.event/territory-assigned
  [state event]
  (assoc-in state [::territories (:congregation/id event) (:territory/id event) :territory/assignments (:assignment/id event)]
            (select-keys event [:assignment/id :assignment/start-date :publisher/id])))

(defmethod projection :territory.event/territory-covered
  [state event]
  (update-in state [::territories (:congregation/id event) (:territory/id event) :territory/assignments (:assignment/id event)]
             (fn [assignment]
               (update assignment :assignment/covered-dates conj-set (:assignment/covered-date event)))))

(defmethod projection :territory.event/territory-returned
  [state event]
  (update-in state [::territories (:congregation/id event) (:territory/id event) :territory/assignments (:assignment/id event)]
             (fn [assignment]
               (assoc assignment :assignment/end-date (:assignment/end-date event)))))


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
        assignments (:territory/assignments territory)]
    (check-permit [:assign-territory cong-id territory-id publisher-id])
    (when-not (contains? assignments assignment-id)
      (when (some #(nil? (:assignment/end-date %)) ; TODO: simplify checking latest assignment
                  (vals assignments))
        (throw (ValidationException. [[:already-assigned cong-id territory-id]])))
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
        end-date (:date command)
        assignment (get-in territory [:territory/assignments assignment-id])]
    (when (nil? assignment)
      (throw (ValidationException. [[:no-such-assignment cong-id territory-id assignment-id]])))
    (check-permit [:assign-territory cong-id territory-id (:publisher/id assignment)])
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
