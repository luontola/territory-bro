;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin)

(defmulti projection (fn [_state event] (:event/type event)))

(defmethod projection :default
  [state _event]
  state)

(defmethod projection :congregation.event/congregation-created
  [state event]
  (-> state
      (assoc-in [::congregations (:congregation/id event)] (select-keys event [:congregation/schema-name]))
      (update ::tenant-schemas-to-create (fnil conj #{}) (select-keys event [:congregation/schema-name]))))

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (let [cong (get-in state [::congregations (:congregation/id event)])
        gis-user (select-keys (merge cong event)
                              [:gis-user/username
                               :gis-user/password
                               :congregation/schema-name])]
    (update state ::gis-users-to-create (fnil conj #{}) gis-user)))
