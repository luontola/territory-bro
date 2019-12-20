;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin
  (:require [territory-bro.commands :as commands]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.events :as events]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.presence-tracker :as presence-tracker]
            [territory-bro.util :refer [conj-set]])
  (:import (territory_bro ValidationException)))

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
        user-id (:user/id event)
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
        (update ::users conj-set user-id)
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

(defn generate-commands [state {:keys [now]}]
  (concat
   (for [tenant (->> (presence-tracker/creatable state ::tracked-congregations)
                     (map #(get-in state [::congregations %])))]
     {:command/type :db-admin.command/migrate-tenant-schema
      :command/time (now)
      :command/system system
      :congregation/id (:congregation/id tenant)
      :congregation/schema-name (:congregation/schema-name tenant)})

   (for [gis-user (->> (presence-tracker/creatable state ::tracked-gis-users)
                       (map #(get-in state [::gis-users %])))]
     {:command/type :db-admin.command/ensure-gis-user-present
      :command/time (now)
      :command/system system
      :user/id (:user/id gis-user)
      :gis-user/username (:gis-user/username gis-user)
      :gis-user/password (:gis-user/password gis-user)
      :congregation/id (:congregation/id gis-user)
      :congregation/schema-name (:congregation/schema-name gis-user)})

   (for [gis-user (->> (presence-tracker/deletable state ::tracked-gis-users)
                       (map #(get-in state [::gis-users %])))]
     {:command/type :db-admin.command/ensure-gis-user-absent
      :command/time (now)
      :command/system system
      :user/id (:user/id gis-user)
      :gis-user/username (:gis-user/username gis-user)
      :congregation/id (:congregation/id gis-user)
      :congregation/schema-name (:congregation/schema-name gis-user)})))


(defmulti ^:private command-handler (fn [command _state _injections] (:command/type command)))

(defn- check-congregation-exists [state cong-id]
  (when-not (contains? (::congregations state) cong-id)
    (throw (ValidationException. [[:no-such-congregation cong-id]]))))

(defn- check-user-exists [state user-id]
  (when-not (contains? (::users state) user-id)
    (throw (ValidationException. [[:no-such-user user-id]]))))

(defmethod command-handler :db-admin.command/migrate-tenant-schema [command state {:keys [migrate-tenant-schema! check-permit]}]
  (let [cong-id (:congregation/id command)]
    (check-permit [:migrate-tenant-schema cong-id])
    (check-congregation-exists state cong-id)
    (migrate-tenant-schema! (:congregation/schema-name command))
    [{:event/type :db-admin.event/gis-schema-is-present
      :event/version 1
      :event/transient? true
      :congregation/id cong-id
      :congregation/schema-name (:congregation/schema-name command)}]))

(defmethod command-handler :db-admin.command/ensure-gis-user-present [command state {:keys [ensure-gis-user-present! check-permit]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:ensure-gis-user-present cong-id user-id])
    (check-congregation-exists state cong-id)
    (check-user-exists state user-id)
    (ensure-gis-user-present! {:username (:gis-user/username command)
                               :password (:gis-user/password command)
                               :schema (:congregation/schema-name command)})
    [{:event/type :db-admin.event/gis-user-is-present
      :event/version 1
      :event/transient? true
      :congregation/id cong-id
      :user/id user-id
      :gis-user/username (:gis-user/username command)}]))

(defmethod command-handler :db-admin.command/ensure-gis-user-absent [command state {:keys [ensure-gis-user-absent! check-permit]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:ensure-gis-user-absent cong-id user-id])
    (check-congregation-exists state cong-id)
    (check-user-exists state user-id)
    (ensure-gis-user-absent! {:username (:gis-user/username command)
                              :schema (:congregation/schema-name command)})
    [{:event/type :db-admin.event/gis-user-is-absent
      :event/version 1
      :event/transient? true
      :congregation/id (:congregation/id command)
      :user/id (:user/id command)
      :gis-user/username (:gis-user/username command)}]))

(defn handle-command!
  ([command state]
   (let [injections {:now (:now config/env)
                     :check-permit (fn [permit]
                                     (commands/check-permit state command permit))
                     :migrate-tenant-schema! db/migrate-tenant-schema!
                     :ensure-gis-user-present! (fn [args]
                                                 (db/with-db [conn {}]
                                                   (gis-user/ensure-present! conn args)))
                     :ensure-gis-user-absent! (fn [args]
                                                (db/with-db [conn {}]
                                                  (gis-user/ensure-absent! conn args)))}]
     (handle-command! command state injections)))
  ([command state injections]
   (->> (command-handler command state injections)
        (events/enrich-events command injections)
        (events/validate-events)))) ; XXX: validated here because transient events are not saved and validated on save
