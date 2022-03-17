;; Copyright © 2015-2022 Esko Luontola
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

(defmethod projection :region.event/region-defined
  [state event]
  (update-in state [::regions (:congregation/id event) (:region/id event)]
             (fn [region]
               (-> region
                   (assoc :region/id (:region/id event))
                   (assoc :region/name (:region/name event))
                   (assoc :region/location (:region/location event))))))

(defmethod projection :region.event/region-deleted
  [state event]
  (dissoc-in state [::regions (:congregation/id event) (:region/id event)]))


;;;; Queries

(defn check-region-exists [state cong-id region-id]
  (when (nil? (get-in state [::regions cong-id region-id]))
    (throw (ValidationException. [[:no-such-region cong-id region-id]]))))


;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::regions (:congregation/id command) (:region/id command)])))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _region _injections]
                                      (:command/type command)))

(def ^:private data-keys
  [:region/name
   :region/location])

(defmethod command-handler :region.command/define-region
  [command region {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        region-id (:region/id command)
        old-data (select-keys region data-keys)
        new-data (select-keys command data-keys)]
    (check-permit [:define-region cong-id region-id])
    (when (not= old-data new-data)
      [(merge {:event/type :region.event/region-defined
               :congregation/id cong-id
               :region/id region-id}
              (gis-change/event-metadata command)
              new-data)])))

(defmethod command-handler :region.command/delete-region
  [command region {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        region-id (:region/id command)]
    (check-permit [:delete-region cong-id region-id])
    (when (some? region)
      [(merge {:event/type :region.event/region-deleted
               :congregation/id cong-id
               :region/id region-id}
              (gis-change/event-metadata command))])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
