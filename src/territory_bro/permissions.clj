;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions)

(def ^:private conj-set (fnil conj #{}))

(defn grant [state user-id permission]
  (update-in state [::permissions user-id (second permission)] conj-set (first permission)))

(defn- revoke-impl [m ks v]
  (if (empty? ks)
    (disj m v)
    (let [m (update m (first ks) revoke-impl (rest ks) v)]
      (if (empty? (get m (first ks)))
        (dissoc m (first ks))
        m))))

(defn revoke [state user-id permission]
  (revoke-impl state [::permissions user-id (second permission)] (first permission)))
