;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin)

(defmulti projection (fn [_state event] (:event/type event)))
(defmethod projection :default [state _event] state)

(defmethod projection :congregation.event/congregation-created
  [state event]
  (let [cong (select-keys event [:congregation/id
                                 :congregation/schema-name])]
    (-> state
        (assoc-in [::congregations (:congregation/id event)] cong)
        (update ::pending-schemas (fnil conj #{}) cong))))

(defmethod projection :db-admin.event/gis-schema-is-present
  [state event]
  (update state ::pending-schemas disj (select-keys event [:congregation/id
                                                           :congregation/schema-name])))

(defn- add-pending-gis-user [state event desired-state]
  (let [cong-id (:congregation/id event)
        user-id (:user/id event)
        cong (get-in state [::congregations cong-id])
        gis-user (-> (merge cong event)
                     (select-keys [:gis-user/username
                                   :gis-user/password
                                   :congregation/schema-name])
                     (assoc ::desired-state desired-state))]
    (assoc-in state [::pending-gis-users [cong-id user-id]] gis-user)))

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (add-pending-gis-user state event :present))

(defmethod projection :congregation.event/gis-user-deleted
  [state event]
  (add-pending-gis-user state event :absent))


(defn- remove-pending-gis-user [state event desired-state]
  (let [cong-id (:congregation/id event)
        user-id (:user/id event)]
    (if (= desired-state (get-in state [::pending-gis-users [cong-id user-id] ::desired-state]))
      (update state ::pending-gis-users dissoc [cong-id user-id])
      state)))

(defmethod projection :db-admin.event/gis-user-is-present
  [state event]
  (remove-pending-gis-user state event :present))

(defmethod projection :db-admin.event/gis-user-is-absent
  [state event]
  (remove-pending-gis-user state event :absent))

(defn process-pending-changes! [state {:keys [dispatch!
                                              migrate-tenant-schema!
                                              ensure-gis-user-present!
                                              ensure-gis-user-absent!]}]
  (doseq [tenant (::pending-schemas state)]
    (migrate-tenant-schema! (:congregation/schema-name tenant))
    (dispatch! {:event/type :db-admin.event/gis-schema-is-present
                :event/version 1
                :event/transient? true
                :congregation/id (:congregation/id tenant)
                :congregation/schema-name (:congregation/schema-name tenant)}))

  (doseq [[[cong-id user-id] gis-user] (::pending-gis-users state)]
    (case (::desired-state gis-user)
      :present (do
                 (ensure-gis-user-present! {:username (:gis-user/username gis-user)
                                            :password (:gis-user/password gis-user)
                                            :schema (:congregation/schema-name gis-user)})
                 (dispatch! {:event/type :db-admin.event/gis-user-is-present
                             :event/version 1
                             :event/transient? true
                             :congregation/id cong-id
                             :user/id user-id
                             :gis-user/username (:gis-user/username gis-user)}))
      :absent (do
                (ensure-gis-user-absent! {:username (:gis-user/username gis-user)
                                          :schema (:congregation/schema-name gis-user)})
                (dispatch! {:event/type :db-admin.event/gis-user-is-absent
                            :event/version 1
                            :event/transient? true
                            :congregation/id cong-id
                            :user/id user-id
                            :gis-user/username (:gis-user/username gis-user)})))))