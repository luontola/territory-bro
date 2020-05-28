;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.facade
  (:require [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.territory :as territory]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.permissions :as permissions]))

(defn- merge-congregation-details [congregation state user-id]
  ;; TODO: fine-grained permission checks; filter territories, regions, users etc.
  (let [cong-id (:congregation/id congregation)]
    {:id (:congregation/id congregation)
     :name (:congregation/name congregation)
     :permissions (->> (permissions/list-permissions state user-id [cong-id])
                       (map (fn [permission]
                              [permission true]))
                       (into {}))
     :users (for [user-id (congregation/get-users state cong-id)]
              {:id user-id})
     ;; TODO: extract query functions
     :territories (sequence (vals (get-in state [::territory/territories cong-id])))
     :congregation-boundaries (sequence (vals (get-in state [::congregation-boundary/congregation-boundaries cong-id])))
     :regions (sequence (vals (get-in state [::region/regions cong-id])))
     :card-minimap-viewports (sequence (vals (get-in state [::card-minimap-viewport/card-minimap-viewports cong-id])))}))

(defn get-my-congregation [state cong-id user-id]
  (some-> (congregation/get-my-congregation state cong-id user-id)
          (merge-congregation-details state user-id)))

(defn get-demo-congregation [state user-id]
  (when-some [cong-id (:demo-congregation config/env)]
    (some-> (congregation/get-unrestricted-congregation state cong-id)
            (merge-congregation-details state user-id)
            (assoc :id "demo")
            (assoc :name "Demo Congregation")
            (assoc :permissions {:view-congregation true})
            (assoc :users []))))
