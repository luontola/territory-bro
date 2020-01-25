;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.card-minimap-viewport
  (:require [medley.core :refer [dissoc-in]]))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defmethod projection :card-minimap-viewport.event/card-minimap-viewport-defined
  [state event]
  (update-in state [::card-minimap-viewports (:congregation/id event) (:card-minimap-viewport/id event)]
             (fn [card-minimap-viewport]
               (-> card-minimap-viewport
                   (assoc :card-minimap-viewport/id (:card-minimap-viewport/id event))
                   (assoc :card-minimap-viewport/location (:card-minimap-viewport/location event))))))

(defmethod projection :card-minimap-viewport.event/card-minimap-viewport-deleted
  [state event]
  (dissoc-in state [::card-minimap-viewports (:congregation/id event) (:card-minimap-viewport/id event)]))


;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::card-minimap-viewports (:congregation/id command) (:card-minimap-viewport/id command)])))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _card-minimap-viewport _injections]
                                      (:command/type command)))

(def ^:private data-keys
  [:card-minimap-viewport/location])

(defmethod command-handler :card-minimap-viewport.command/create-card-minimap-viewport
  [command card-minimap-viewport {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        card-minimap-viewport-id (:card-minimap-viewport/id command)]
    (check-permit [:create-card-minimap-viewport cong-id])
    (when (nil? card-minimap-viewport)
      [(merge {:event/type :card-minimap-viewport.event/card-minimap-viewport-defined
               :event/version 1
               :congregation/id cong-id
               :card-minimap-viewport/id card-minimap-viewport-id}
              (select-keys command data-keys))])))

(defmethod command-handler :card-minimap-viewport.command/update-card-minimap-viewport
  [command card-minimap-viewport {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        card-minimap-viewport-id (:card-minimap-viewport/id command)
        old-data (select-keys card-minimap-viewport data-keys)
        new-data (select-keys command data-keys)]
    (check-permit [:update-card-minimap-viewport cong-id card-minimap-viewport-id])
    (when (not= old-data new-data)
      [(merge {:event/type :card-minimap-viewport.event/card-minimap-viewport-defined
               :event/version 1
               :congregation/id cong-id
               :card-minimap-viewport/id card-minimap-viewport-id}
              new-data)])))

(defmethod command-handler :card-minimap-viewport.command/delete-card-minimap-viewport
  [command card-minimap-viewport {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        card-minimap-viewport-id (:card-minimap-viewport/id command)]
    (check-permit [:delete-card-minimap-viewport cong-id card-minimap-viewport-id])
    (when (some? card-minimap-viewport)
      [{:event/type :card-minimap-viewport.event/card-minimap-viewport-deleted
        :event/version 1
        :congregation/id cong-id
        :card-minimap-viewport/id card-minimap-viewport-id}])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
