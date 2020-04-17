;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.territory
  (:require [medley.core :refer [dissoc-in]]
            [territory-bro.gis.gis-change :as gis-change])
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


;;;; Queries

(defn check-territory-exists [state cong-id territory-id]
  (when (nil? (get-in state [::territories cong-id territory-id]))
    (throw (ValidationException. [[:no-such-territory cong-id territory-id]]))))


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

(defmethod command-handler :territory.command/create-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)]
    (check-permit [:create-territory cong-id])
    (when (nil? territory)
      [(merge {:event/type :territory.event/territory-defined
               :event/version 1
               :congregation/id cong-id
               :territory/id territory-id}
              (gis-change/event-metadata command)
              (select-keys command data-keys))])))

(defmethod command-handler :territory.command/update-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)
        old-data (select-keys territory data-keys)
        new-data (select-keys command data-keys)]
    (check-permit [:update-territory cong-id territory-id])
    (when (not= old-data new-data)
      [(merge {:event/type :territory.event/territory-defined
               :event/version 1
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
               :event/version 1
               :congregation/id cong-id
               :territory/id territory-id}
              (gis-change/event-metadata command))])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
