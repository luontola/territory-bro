;; Copyright © 2015-2023 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.facade
  (:require [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.territory :as territory]
            [territory-bro.infra.permissions :as permissions]))

(defn- enrich-congregation [cong state user-id]
  (let [cong-id (:congregation/id cong)]
    {:congregation/id cong-id
     :congregation/name (:congregation/name cong)
     :congregation/loans-csv-url (:congregation/loans-csv-url cong)
     :congregation/permissions (->> (permissions/list-permissions state user-id [cong-id])
                                    (map (fn [permission]
                                           [permission true]))
                                    (into {}))
     :congregation/users (for [user-id (congregation/get-users state cong-id)]
                           {:user/id user-id})
     ;; TODO: extract query functions
     :congregation/territories (sequence (vals (get-in state [::territory/territories cong-id])))
     :congregation/congregation-boundaries (sequence (vals (get-in state [::congregation-boundary/congregation-boundaries cong-id])))
     :congregation/regions (sequence (vals (get-in state [::region/regions cong-id])))
     :congregation/card-minimap-viewports (sequence (vals (get-in state [::card-minimap-viewport/card-minimap-viewports cong-id])))}))

;; TODO: deduplicate with congregation/apply-user-permissions
(defn- apply-user-permissions [cong state user-id]
  (let [cong-id (:congregation/id cong)
        territory-ids (lazy-seq (for [[_ _ territory-id] (permissions/match state user-id [:view-territory cong-id '*])]
                                  territory-id))]
    (cond
      (permissions/allowed? state user-id [:view-congregation cong-id])
      cong

      (not (empty? territory-ids))
      (-> cong
          (assoc :congregation/users [])
          (assoc :congregation/congregation-boundaries [])
          (assoc :congregation/regions [])
          (assoc :congregation/card-minimap-viewports [])
          (assoc :congregation/territories (for [territory-id territory-ids]
                                             (get-in state [::territory/territories cong-id territory-id]))))

      :else
      nil)))

(defn get-congregation [state cong-id user-id]
  (some-> (congregation/get-unrestricted-congregation state cong-id)
          (enrich-congregation state user-id)
          (apply-user-permissions state user-id)))

(defn get-demo-congregation [state cong-id user-id]
  (when cong-id
    (some-> (congregation/get-unrestricted-congregation state cong-id)
            (enrich-congregation state user-id)
            (assoc :congregation/id "demo")
            (assoc :congregation/name "Demo Congregation")
            (assoc :congregation/loans-csv-url nil)
            (assoc :congregation/permissions {:view-congregation true
                                              :share-territory-link true})
            (assoc :congregation/users []))))
