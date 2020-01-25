;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.subregion
  (:require [medley.core :refer [dissoc-in]]))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defmethod projection :subregion.event/subregion-defined
  [state event]
  (update-in state [::subregions (:congregation/id event) (:subregion/id event)]
             (fn [subregion]
               (-> subregion
                   (assoc :subregion/id (:subregion/id event))
                   (assoc :subregion/name (:subregion/name event))
                   (assoc :subregion/location (:subregion/location event))))))

(defmethod projection :subregion.event/subregion-deleted
  [state event]
  (dissoc-in state [::subregions (:congregation/id event) (:subregion/id event)]))


;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::subregions (:congregation/id command) (:subregion/id command)])))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _subregion _injections]
                                      (:command/type command)))

(def ^:private data-keys
  [:subregion/name
   :subregion/location])

(defmethod command-handler :subregion.command/create-subregion
  [command subregion {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        subregion-id (:subregion/id command)]
    (check-permit [:create-subregion cong-id])
    (when (nil? subregion)
      [(merge {:event/type :subregion.event/subregion-defined
               :event/version 1
               :congregation/id cong-id
               :subregion/id subregion-id}
              (select-keys command data-keys))])))

(defmethod command-handler :subregion.command/update-subregion
  [command subregion {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        subregion-id (:subregion/id command)
        old-data (select-keys subregion data-keys)
        new-data (select-keys command data-keys)]
    (check-permit [:update-subregion cong-id subregion-id])
    (when (not= old-data new-data)
      [(merge {:event/type :subregion.event/subregion-defined
               :event/version 1
               :congregation/id cong-id
               :subregion/id subregion-id}
              new-data)])))

(defmethod command-handler :subregion.command/delete-subregion
  [command subregion {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        subregion-id (:subregion/id command)]
    (check-permit [:delete-subregion cong-id subregion-id])
    (when (some? subregion)
      [{:event/type :subregion.event/subregion-deleted
        :event/version 1
        :congregation/id cong-id
        :subregion/id subregion-id}])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
