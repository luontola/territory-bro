(ns territory-bro.infra.permissions
  (:require [medley.core :refer [dissoc-in]])
  (:import (territory_bro NoPermitException)))

;; The search index is arranged so that the permission keyword
;; is the last element, even though outside this module it's
;; always the first element, because that makes it easier to
;; find all permissions that relate to a particular scope.
;; The public permit format must be twisted to the internal
;; format and then untwisted back to the public format.

(defn- twist [permit]
  (concat (rest permit) (cons (first permit) nil)))

(defn- untwist [permit]
  (cons (last permit) (drop-last permit)))

(defn- permit? [permit]
  (let [[permission & scope] permit]
    (and (keyword? permission)
         (every? some? scope))))

(defn- path [user-id permit]
  (assert (permit? permit) {:permit permit})
  (->> (twist permit)
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

(defn list-permissions [state user-id scope]
  (let [parent-path (drop-last (path user-id (cons :dummy scope)))
        permission-map (get-in state parent-path)
        permissions (set (filter keyword? (keys permission-map)))]
    (if (empty? scope)
      permissions
      (into permissions (list-permissions state user-id (drop-last scope))))))

(defn- match0 [haystack matched needle]
  (let [[k & ks] needle]
    (cond
      ;; end of search tree
      (true? haystack) (when (empty? needle)
                         [matched])
      ;; exact match
      (contains? haystack k) (recur (get haystack k)
                                    (conj matched k)
                                    ks)
      ;; wildcard match
      (= '* k) (mapcat (fn [k]
                         (match0 (get haystack k)
                                 (conj matched k)
                                 ks))
                       (keys haystack)))))

(defn match [state user-id matcher]
  (->> (match0 (get-in state [::permissions user-id]) [] (twist matcher))
       (map untwist)))
