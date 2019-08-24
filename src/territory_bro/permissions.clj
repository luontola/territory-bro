;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions)

(def ^:private conj-set (fnil conj #{}))

(defn grant [state user-id permission]
  (update-in state [::permissions user-id (second permission)] conj-set (first permission)))

(defn- disj-in [m ks to-be-removed]
  (if (empty? ks)
    (disj m to-be-removed)
    (let [k (first ks)
          v (disj-in (get m k) (rest ks) to-be-removed)]
      (if (empty? v)
        (dissoc m k)
        (assoc m k v)))))

(defn revoke [state user-id permission]
  (disj-in state [::permissions user-id (second permission)] (first permission)))

(defn allowed? [state user-id permission]
  (boolean (get-in state [::permissions user-id (second permission) (first permission)])))
