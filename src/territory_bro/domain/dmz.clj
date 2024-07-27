;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.dmz
  (:require [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.loan :as loan]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.territory :as territory]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.permissions :as permissions]))

(def ^:dynamic *state* nil)

(defn- enrich-congregation [cong]
  (let [user-id (auth/current-user-id)
        cong-id (:congregation/id cong)]
    {:congregation/id cong-id
     :congregation/name (:congregation/name cong)
     :congregation/loans-csv-url (:congregation/loans-csv-url cong)
     :congregation/permissions (->> (permissions/list-permissions *state* user-id [cong-id])
                                    (map (fn [permission]
                                           [permission true]))
                                    (into {}))
     :congregation/users (for [user-id (congregation/get-users *state* cong-id)]
                           {:user/id user-id})
     ;; TODO: extract query functions
     :congregation/territories (sequence (vals (get-in *state* [::territory/territories cong-id])))
     :congregation/congregation-boundaries (sequence (vals (get-in *state* [::congregation-boundary/congregation-boundaries cong-id])))
     :congregation/regions (sequence (vals (get-in *state* [::region/regions cong-id])))
     :congregation/card-minimap-viewports (sequence (vals (get-in *state* [::card-minimap-viewport/card-minimap-viewports cong-id])))}))

(defn- apply-user-permissions-for-congregation [cong state]
  (let [user-id (auth/current-user-id)
        cong-id (:congregation/id cong)
        ;; TODO: move territory fetching to list-territories
        territory-ids (lazy-seq (for [[_ _ territory-id] (permissions/match state user-id [:view-territory cong-id '*])]
                                  territory-id))]
    (cond
      ;; TODO: deduplicate with congregation/apply-user-permissions
      (permissions/allowed? state user-id [:view-congregation cong-id])
      cong

      ;; TODO: introduce a :view-congregation-temporarily permission when opening shares?
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

(defn get-own-congregation [state cong-id]
  (some-> (congregation/get-unrestricted-congregation state cong-id)
          (enrich-congregation)
          (apply-user-permissions-for-congregation state)))

(defn get-demo-congregation [state cong-id]
  (when cong-id
    (some-> (congregation/get-unrestricted-congregation state cong-id)
            (enrich-congregation)
            (assoc :congregation/id "demo")
            (assoc :congregation/name "Demo Congregation")
            (assoc :congregation/loans-csv-url nil)
            (assoc :congregation/permissions {:view-congregation true
                                              :share-territory-link true})
            (assoc :congregation/users []))))

(defn get-congregation [state cong-id]
  (if (= "demo" cong-id)
    (get-demo-congregation state (:demo-congregation config/env))
    (get-own-congregation state cong-id)))

(defn list-congregations [state]
  (let [user-id (auth/current-user-id)]
    (congregation/get-my-congregations state user-id)))


(defn- enrich-do-not-calls [territory conn cong-id territory-id]
  (merge territory
         (-> (do-not-calls/get-do-not-calls conn cong-id territory-id)
             (select-keys [:territory/do-not-calls]))))

(defn- apply-user-permissions-for-territory [territory state]
  (let [user-id (auth/current-user-id)
        cong-id (:congregation/id territory)
        territory-id (:territory/id territory)]
    (when (or (permissions/allowed? state user-id [:view-congregation cong-id])
              (permissions/allowed? state user-id [:view-territory cong-id territory-id]))
      territory)))

(defn get-own-territory [conn state cong-id territory-id]
  (some-> (territory/get-unrestricted-territory state cong-id territory-id)
          (enrich-do-not-calls conn cong-id territory-id)
          (apply-user-permissions-for-territory state)))

(defn get-demo-territory [state cong-id territory-id]
  (when cong-id
    (some-> (territory/get-unrestricted-territory state cong-id territory-id)
            (assoc :congregation/id "demo"))))

(defn get-territory [conn state cong-id territory-id]
  (if (= "demo" cong-id)
    (get-demo-territory state (:demo-congregation config/env) territory-id)
    (get-own-territory conn state cong-id territory-id)))

(defn list-territories! [state cong-id {:keys [fetch-loans?]}]
  ;; TODO: inline get-own-congregation and only get the territories instead of everything in the congregation
  (let [user-id (auth/current-user-id)
        congregation (get-congregation state cong-id)
        fetch-loans? (and fetch-loans?
                          (permissions/allowed? state user-id [:view-congregation cong-id])
                          (some? (:congregation/loans-csv-url congregation)))]
    (when congregation
      (:congregation/territories (cond-> congregation
                                   fetch-loans? (loan/enrich-territory-loans!))))))
