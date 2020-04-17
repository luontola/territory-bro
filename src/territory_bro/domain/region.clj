;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.region
  (:require [medley.core :refer [dissoc-in]]
            [territory-bro.gis.gis-change :as gis-change])
  (:import (territory_bro ValidationException)))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defmethod projection :subregion.event/subregion-defined
  [state event]
  (update-in state [::regions (:congregation/id event) (:subregion/id event)]
             (fn [region]
               (-> region
                   (assoc :subregion/id (:subregion/id event))
                   (assoc :subregion/name (:subregion/name event))
                   (assoc :subregion/location (:subregion/location event))))))

(defmethod projection :subregion.event/subregion-deleted
  [state event]
  (dissoc-in state [::regions (:congregation/id event) (:subregion/id event)]))


;;;; Queries

(defn check-region-exists [state cong-id region-id]
  (when (nil? (get-in state [::regions cong-id region-id]))
    (throw (ValidationException. [[:no-such-region cong-id region-id]]))))


;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::regions (:congregation/id command) (:subregion/id command)])))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _region _injections]
                                      (:command/type command)))

(def ^:private data-keys
  [:subregion/name
   :subregion/location])

(defmethod command-handler :region.command/create-region
  [command region {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        region-id (:subregion/id command)]
    (check-permit [:create-region cong-id])
    (when (nil? region)
      [(merge {:event/type :subregion.event/subregion-defined
               :event/version 1
               :congregation/id cong-id
               :subregion/id region-id}
              (gis-change/event-metadata command)
              (select-keys command data-keys))])))

(defmethod command-handler :region.command/update-region
  [command region {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        region-id (:subregion/id command)
        old-data (select-keys region data-keys)
        new-data (select-keys command data-keys)]
    (check-permit [:update-region cong-id region-id])
    (when (not= old-data new-data)
      [(merge {:event/type :subregion.event/subregion-defined
               :event/version 1
               :congregation/id cong-id
               :subregion/id region-id}
              (gis-change/event-metadata command)
              new-data)])))

(defmethod command-handler :region.command/delete-region
  [command region {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        region-id (:subregion/id command)]
    (check-permit [:delete-region cong-id region-id])
    (when (some? region)
      [(merge {:event/type :subregion.event/subregion-deleted
               :event/version 1
               :congregation/id cong-id
               :subregion/id region-id}
              (gis-change/event-metadata command))])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
