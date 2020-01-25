;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-boundary
  (:require [medley.core :refer [dissoc-in]]))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defmethod projection :congregation-boundary.event/congregation-boundary-defined
  [state event]
  (update-in state [::congregation-boundaries (:congregation/id event) (:congregation-boundary/id event)]
             (fn [congregation-boundary]
               (-> congregation-boundary
                   (assoc :congregation-boundary/id (:congregation-boundary/id event))
                   (assoc :congregation-boundary/location (:congregation-boundary/location event))))))

(defmethod projection :congregation-boundary.event/congregation-boundary-deleted
  [state event]
  (dissoc-in state [::congregation-boundaries (:congregation/id event) (:congregation-boundary/id event)]))


;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::congregation-boundaries (:congregation/id command) (:congregation-boundary/id command)])))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _congregation-boundary _injections]
                                      (:command/type command)))

(def ^:private data-keys
  [:congregation-boundary/location])

(defmethod command-handler :congregation-boundary.command/create-congregation-boundary
  [command congregation-boundary {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        congregation-boundary-id (:congregation-boundary/id command)]
    (check-permit [:create-congregation-boundary cong-id])
    (when (nil? congregation-boundary)
      [(merge {:event/type :congregation-boundary.event/congregation-boundary-defined
               :event/version 1
               :congregation/id cong-id
               :congregation-boundary/id congregation-boundary-id}
              (select-keys command data-keys))])))

(defmethod command-handler :congregation-boundary.command/update-congregation-boundary
  [command congregation-boundary {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        congregation-boundary-id (:congregation-boundary/id command)
        old-data (select-keys congregation-boundary data-keys)
        new-data (select-keys command data-keys)]
    (check-permit [:update-congregation-boundary cong-id congregation-boundary-id])
    (when (not= old-data new-data)
      [(merge {:event/type :congregation-boundary.event/congregation-boundary-defined
               :event/version 1
               :congregation/id cong-id
               :congregation-boundary/id congregation-boundary-id}
              new-data)])))

(defmethod command-handler :congregation-boundary.command/delete-congregation-boundary
  [command congregation-boundary {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        congregation-boundary-id (:congregation-boundary/id command)]
    (check-permit [:delete-congregation-boundary cong-id congregation-boundary-id])
    (when (some? congregation-boundary)
      [{:event/type :congregation-boundary.event/congregation-boundary-deleted
        :event/version 1
        :congregation/id cong-id
        :congregation-boundary/id congregation-boundary-id}])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
