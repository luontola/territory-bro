;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.permissions
  (:require [medley.core :refer [dissoc-in]])
  (:import (territory_bro NoPermitException)))

;; TODO: rename resource-ids to context or scope?
;; TODO: deduplicate with twist/untwist?
(defn- path [user-id [permission & resource-ids]]
  (assert (keyword? permission) {:permission permission})
  (assert (every? some? resource-ids) {:resource-ids resource-ids})
  (->> nil
       (cons permission)
       (concat resource-ids)
       (cons user-id)
       (cons ::permissions)))

(defn grant [state user-id permit]
  (assoc-in state (path user-id permit) true))

(defn revoke [state user-id permit]
  (dissoc-in state (path user-id permit)))

(defn allowed? [state user-id permit]
  (cond
    (empty? permit) false
    ;; has exact permit?
    (true? (get-in state (path user-id permit))) true
    ;; has broader permit?
    :else (recur state user-id (drop-last permit))))

(defn check [state user-id permit]
  (when-not (allowed? state user-id permit)
    (throw (NoPermitException. user-id permit))))

(defn list-permissions [state user-id resource-ids]
  (let [parent-path (drop-last (path user-id (cons :dummy resource-ids)))
        permision-map (get-in state parent-path)
        permissions (set (filter keyword? (keys permision-map)))]
    (if (empty? resource-ids)
      permissions
      (into permissions (list-permissions state user-id (drop-last resource-ids))))))

(defn- match0 [m matched remaining]
  (let [[x & xs] remaining]
    (cond
      (nil? x) []
      (empty? xs) (if (true? (get m x))
                    [(conj matched x)]
                    [])
      :else (recur (get m x) (conj matched x) xs))))

(defn- twist [xs]
  (concat (rest xs) [(first xs)]))

(defn- untwist [xs]
  (cons (last xs) (drop-last xs)))

(defn match [state user-id matcher]
  (let [user-permits (get-in state [::permissions user-id])]
    (->> (match0 user-permits [] (twist matcher))
         (map untwist))))
