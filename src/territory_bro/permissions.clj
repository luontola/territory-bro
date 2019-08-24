;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions
  (:require [medley.core :refer [dissoc-in]]))

(defn- path [user-id permission]
  (concat [::permissions user-id]
          (rest permission)
          [(first permission)]))

(defn grant [state user-id permission]
  (assoc-in state (path user-id permission) true))

(defn revoke [state user-id permission]
  (dissoc-in state (path user-id permission)))

(defn allowed? [state user-id permission]
  (cond
    (empty? permission) false
    ;; has exact permission?
    (get-in state (path user-id permission)) true
    ;; has broader permission?
    :else (recur state user-id (drop-last permission))))
