;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions)

(def ^:private conj-set (fnil conj #{}))

(defn grant [state user-id permission]
  (update-in state [::permissions user-id (second permission)] conj-set (first permission)))

(defn revoke [state user-id permission]
  (let [state (update-in state [::permissions user-id (second permission)] disj (first permission))
        state (if (empty? (get-in state [::permissions user-id (second permission)]))
                (update-in state [::permissions user-id] dissoc (second permission))
                state)
        state (if (empty? (get-in state [::permissions user-id]))
                (update-in state [::permissions] dissoc user-id)
                state)]
    state))
