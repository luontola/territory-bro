;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.territory
  (:require [medley.core :refer [dissoc-in]]))

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
                   (assoc :territory/subregion (:territory/subregion event))
                   (assoc :territory/meta (:territory/meta event))
                   (assoc :territory/location (:territory/location event))))))

(defmethod projection :territory.event/territory-deleted
  [state event]
  (dissoc-in state [::territories (:congregation/id event) (:territory/id event)]))


;;;; Write model

(defn- write-model [events]
  (let [[{cong-id :congregation/id, territory-id :territory/id}] events]
    (-> (reduce projection nil events)
        (get-in [::territories cong-id territory-id]))))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _territory _injections]
                                      (:command/type command)))

(defmethod command-handler :territory.command/create-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)]
    (check-permit [:create-territory cong-id])
    (when (nil? territory)
      [{:event/type :territory.event/territory-defined
        :event/version 1
        :congregation/id cong-id
        :territory/id territory-id
        :territory/number (:territory/number command)
        :territory/addresses (:territory/addresses command)
        :territory/subregion (:territory/subregion command)
        :territory/meta (:territory/meta command)
        :territory/location (:territory/location command)}])))

(defmethod command-handler :territory.command/update-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)
        old-vals (select-keys territory [:territory/number :territory/addresses :territory/subregion :territory/meta :territory/location])
        new-vals (select-keys command [:territory/number :territory/addresses :territory/subregion :territory/meta :territory/location])]
    (check-permit [:update-territory cong-id territory-id])
    (when (not= old-vals new-vals)
      [{:event/type :territory.event/territory-defined
        :event/version 1
        :congregation/id cong-id
        :territory/id territory-id
        :territory/number (:territory/number command)
        :territory/addresses (:territory/addresses command)
        :territory/subregion (:territory/subregion command)
        :territory/meta (:territory/meta command)
        :territory/location (:territory/location command)}])))

(defmethod command-handler :territory.command/delete-territory
  [command territory {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)]
    (check-permit [:delete-territory cong-id territory-id])
    (when (some? territory)
      [{:event/type :territory.event/territory-deleted
        :event/version 1
        :congregation/id cong-id
        :territory/id territory-id}])))

(defn handle-command [command events injections]
  (command-handler command (write-model events) injections))
