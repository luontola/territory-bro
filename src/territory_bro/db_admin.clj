;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin
  (:require [territory-bro.commands :as commands]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.events :as events]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.util :refer [conj-set]]))

(defmulti projection (fn [_state event] (:event/type event)))
(defmethod projection :default [state _event] state)

(defmethod projection :congregation.event/congregation-created
  [state event]
  (let [cong (select-keys event [:congregation/id
                                 :congregation/schema-name])]
    (-> state
        (assoc-in [::congregations (:congregation/id event)] cong)
        (update ::pending-schemas conj-set cong))))

(defmethod projection :db-admin.event/gis-schema-is-present
  [state event]
  (update state ::pending-schemas disj (select-keys event [:congregation/id
                                                           :congregation/schema-name])))

(defn- add-pending-gis-user [state event desired-state]
  (let [cong-id (:congregation/id event)
        user-id (:user/id event)
        cong (get-in state [::congregations cong-id])
        _ (assert cong {:error "congregation not found"
                        :cong-id cong-id})
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


(def ^:private system (str (ns-name *ns*)))

(defn generate-commands [state {:keys [now]}]
  (concat
   (for [tenant (::pending-schemas state)]
     {:command/type :db-admin.command/migrate-tenant-schema
      :command/time (now)
      :command/system system
      :congregation/id (:congregation/id tenant)
      :congregation/schema-name (:congregation/schema-name tenant)})

   (for [[[cong-id user-id] gis-user] (::pending-gis-users state)]
     (case (::desired-state gis-user)
       :present {:command/type :db-admin.command/ensure-gis-user-present
                 :command/time (now)
                 :command/system system
                 :user/id user-id
                 :gis-user/username (:gis-user/username gis-user)
                 :gis-user/password (:gis-user/password gis-user)
                 :congregation/id cong-id
                 :congregation/schema-name (:congregation/schema-name gis-user)}
       :absent {:command/type :db-admin.command/ensure-gis-user-absent
                :command/time (now)
                :command/system system
                :user/id user-id
                :gis-user/username (:gis-user/username gis-user)
                :congregation/id cong-id
                :congregation/schema-name (:congregation/schema-name gis-user)}))))


(defmulti ^:private command-handler (fn [command _injections] (:command/type command)))

(defmethod command-handler :db-admin.command/migrate-tenant-schema [command {:keys [migrate-tenant-schema!]}]
  (migrate-tenant-schema! (:congregation/schema-name command))
  [{:event/type :db-admin.event/gis-schema-is-present
    :event/version 1
    :event/transient? true
    :congregation/id (:congregation/id command)
    :congregation/schema-name (:congregation/schema-name command)}])

(defmethod command-handler :db-admin.command/ensure-gis-user-present [command {:keys [ensure-gis-user-present!]}]
  (ensure-gis-user-present! {:username (:gis-user/username command)
                             :password (:gis-user/password command)
                             :schema (:congregation/schema-name command)})
  [{:event/type :db-admin.event/gis-user-is-present
    :event/version 1
    :event/transient? true
    :congregation/id (:congregation/id command)
    :user/id (:user/id command)
    :gis-user/username (:gis-user/username command)}])

(defmethod command-handler :db-admin.command/ensure-gis-user-absent [command {:keys [ensure-gis-user-absent!]}]
  (ensure-gis-user-absent! {:username (:gis-user/username command)
                            :schema (:congregation/schema-name command)})
  [{:event/type :db-admin.event/gis-user-is-absent
    :event/version 1
    :event/transient? true
    :congregation/id (:congregation/id command)
    :user/id (:user/id command)
    :gis-user/username (:gis-user/username command)}])

(def ^:private default-injections
  {:now #((:now config/env))
   :migrate-tenant-schema! db/migrate-tenant-schema!
   :ensure-gis-user-present! (fn [args]
                               (db/with-db [conn {}]
                                 (gis-user/ensure-present! conn args)))
   :ensure-gis-user-absent! (fn [args]
                              (db/with-db [conn {}]
                                (gis-user/ensure-absent! conn args)))})

(defn handle-command!
  ([command]
   (handle-command! command default-injections))
  ([command injections]
   (commands/validate-command command) ; TODO: validate all commands centrally
   (->> (command-handler command injections)
        (events/enrich-events command injections)
        (events/validate-events)))) ; XXX: validated here because transient events are not saved and validated on save
