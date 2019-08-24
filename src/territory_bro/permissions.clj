;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions
  (:require [medley.core :refer [dissoc-in]]))

(defn- path [user-id [permission & resource-ids]]
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
    (get-in state (path user-id permit)) true
    ;; has broader permit?
    :else (recur state user-id (drop-last permit))))
