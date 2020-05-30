;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.facade
  (:require [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]
            [territory-bro.infra.permissions :as permissions]))

(defn- enrich-congregation [cong state user-id]
  (let [cong-id (:congregation/id cong)]
    {:id cong-id
     :name (:congregation/name cong)
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

;; TODO: use namespaced keys
;; TODO: deduplicate with congregation/apply-user-permissions
(defn- apply-user-permissions [cong state user-id]
  (let [cong-id (:id cong)
        territory-ids (lazy-seq (for [[_ _ territory-id] (permissions/match state user-id [:view-territory cong-id '*])]
                                  territory-id))]
    (cond
      (permissions/allowed? state user-id [:view-congregation cong-id])
      cong

      (not (empty? territory-ids))
      (-> cong
          (assoc :users [])
          (assoc :congregation-boundaries [])
          (assoc :regions [])
          (assoc :card-minimap-viewports [])
          (assoc :territories (for [territory-id territory-ids]
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
            (assoc :id "demo")
            (assoc :name "Demo Congregation")
            (assoc :permissions {:view-congregation true})
            (assoc :users []))))


;;;; Shares

(defn- grant-opened-share [state share-id user-id] ; TODO: move to share ns
  (if-some [share (get-in state [::share/shares share-id])]
    (permissions/grant state user-id [:view-territory (:congregation/id share) (:territory/id share)])
    state))

(defn grant-opened-shares [state share-ids user-id]
  (reduce #(grant-opened-share %1 %2 user-id) state share-ids))
