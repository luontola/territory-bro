;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.db-admin
  (:require [territory-bro.infra.config :as config]
            [territory-bro.infra.presence-tracker :as presence-tracker]))

(defmulti projection (fn [_state event] (:event/type event)))
(defmethod projection :default [state _event] state)

(defmethod projection :congregation.event/congregation-created
  [state event]
  (let [cong-id (:congregation/id event)
        cong (select-keys event [:congregation/id
                                 :congregation/schema-name])]
    (-> state
        (assoc-in [::congregations cong-id] cong)
        (presence-tracker/set-desired ::tracked-congregations cong-id :present))))

(defmethod projection :db-admin.event/gis-schema-is-present
  [state event]
  (-> state
      (presence-tracker/set-actual ::tracked-congregations (:congregation/id event) :present)))

(defn- gis-user-key [event]
  ;; TODO: use :gis-user/username as the key, in case the username is changed?
  (select-keys event [:congregation/id :user/id]))

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (let [cong-id (:congregation/id event)
        cong (get-in state [::congregations cong-id])
        _ (assert cong {:error "congregation not found", :cong-id cong-id})
        k (gis-user-key event)
        gis-user (-> (merge cong event)
                     (select-keys [:congregation/id
                                   :user/id
                                   :gis-user/username
                                   :gis-user/password
                                   :congregation/schema-name]))]
    (-> state
        (assoc-in [::gis-users k] gis-user)
        (presence-tracker/set-desired ::tracked-gis-users k :present)
        ;; force recreating the user to apply a password change
        (presence-tracker/set-actual ::tracked-gis-users k :absent))))

(defmethod projection :congregation.event/gis-user-deleted
  [state event]
  (-> state
      (presence-tracker/set-desired ::tracked-gis-users (gis-user-key event) :absent)))

(defmethod projection :db-admin.event/gis-user-is-present
  [state event]
  (-> state
      (presence-tracker/set-actual ::tracked-gis-users (gis-user-key event) :present)))

(defmethod projection :db-admin.event/gis-user-is-absent
  [state event]
  (-> state
      (presence-tracker/set-actual ::tracked-gis-users (gis-user-key event) :absent)))


(def ^:private system (str (ns-name *ns*)))

(defn generate-commands [state]
  (concat
   (for [tenant (->> (presence-tracker/creatable state ::tracked-congregations)
                     (map #(get-in state [::congregations %])))]
     {:command/type :db-admin.command/migrate-tenant-schema
      :command/time (config/now)
      :command/system system
      :congregation/id (:congregation/id tenant)
      :congregation/schema-name (:congregation/schema-name tenant)})

   (for [gis-user (->> (presence-tracker/creatable state ::tracked-gis-users)
                       (map #(get-in state [::gis-users %])))]
     {:command/type :db-admin.command/ensure-gis-user-present
      :command/time (config/now)
      :command/system system
      :user/id (:user/id gis-user)
      :gis-user/username (:gis-user/username gis-user)
      :gis-user/password (:gis-user/password gis-user)
      :congregation/id (:congregation/id gis-user)
      :congregation/schema-name (:congregation/schema-name gis-user)})

   (for [gis-user (->> (presence-tracker/deletable state ::tracked-gis-users)
                       (map #(get-in state [::gis-users %])))]
     {:command/type :db-admin.command/ensure-gis-user-absent
      :command/time (config/now)
      :command/system system
      :user/id (:user/id gis-user)
      :gis-user/username (:gis-user/username gis-user)
      :congregation/id (:congregation/id gis-user)
      :congregation/schema-name (:congregation/schema-name gis-user)})))

(defn init-present-schemas [state {:keys [get-present-schemas]}]
  (let [schema->cong-id (->> (vals (::congregations state))
                             (map (fn [cong]
                                    [(:congregation/schema-name cong) (:congregation/id cong)]))
                             (into {}))]
    (for [schema (get-present-schemas)
          :let [cong-id (schema->cong-id schema)]
          :when (some? cong-id)]
      {:event/type :db-admin.event/gis-schema-is-present
       :event/transient? true
       :congregation/id cong-id
       :congregation/schema-name schema})))

(defn init-present-users [state {:keys [get-present-users]}]
  (let [schema->cong-id (->> (vals (::congregations state))
                             (map (fn [cong]
                                    [(:congregation/schema-name cong) (:congregation/id cong)]))
                             (into {}))
        username->user-id (->> (vals (::gis-users state))
                               (map (fn [gis-user]
                                      [(:gis-user/username gis-user) (:user/id gis-user)]))
                               (into {}))]
    (for [{:keys [username schema]} (get-present-users)
          :let [cong-id (schema->cong-id schema)
                user-id (username->user-id username)]
          :when (some? cong-id)
          :when (some? user-id)]
      {:event/type :db-admin.event/gis-user-is-present
       :event/transient? true
       :congregation/id cong-id
       :user/id user-id
       :gis-user/username username})))

(defmulti ^:private command-handler (fn [command _injections] (:command/type command)))

(defmethod command-handler :db-admin.command/migrate-tenant-schema [command {:keys [migrate-tenant-schema! check-permit]}]
  (let [cong-id (:congregation/id command)]
    (check-permit [:migrate-tenant-schema cong-id])
    (migrate-tenant-schema! (:congregation/schema-name command))
    [{:event/type :db-admin.event/gis-schema-is-present
      :event/transient? true
      :congregation/id cong-id
      :congregation/schema-name (:congregation/schema-name command)}]))

(defmethod command-handler :db-admin.command/ensure-gis-user-present [command {:keys [ensure-gis-user-present! check-permit]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:ensure-gis-user-present cong-id user-id])
    (ensure-gis-user-present! {:username (:gis-user/username command)
                               :password (:gis-user/password command)
                               :schema (:congregation/schema-name command)})
    [{:event/type :db-admin.event/gis-user-is-present
      :event/transient? true
      :congregation/id cong-id
      :user/id user-id
      :gis-user/username (:gis-user/username command)}]))

(defmethod command-handler :db-admin.command/ensure-gis-user-absent [command {:keys [ensure-gis-user-absent! check-permit]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:ensure-gis-user-absent cong-id user-id])
    (ensure-gis-user-absent! {:username (:gis-user/username command)
                              :schema (:congregation/schema-name command)})
    [{:event/type :db-admin.event/gis-user-is-absent
      :event/transient? true
      :congregation/id (:congregation/id command)
      :user/id (:user/id command)
      :gis-user/username (:gis-user/username command)}]))

(defn handle-command [command _state injections]
  (command-handler command injections))
