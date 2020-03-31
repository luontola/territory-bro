;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.congregation
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.infra.util :refer [conj-set]])
  (:import (territory_bro ValidationException)))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defn- update-cong [state event f]
  (update-in state [::congregations (:congregation/id event)] f))

(defmethod projection :congregation.event/congregation-created
  [state event]
  (-> state
      (update-cong event (fn [congregation]
                           (-> congregation
                               (assoc :congregation/id (:congregation/id event))
                               (assoc :congregation/name (:congregation/name event))
                               (assoc :congregation/schema-name (:congregation/schema-name event)))))))

(defmethod projection :congregation.event/congregation-renamed
  [state event]
  (-> state
      (update-cong event (fn [congregation]
                           (-> congregation
                               (assoc :congregation/name (:congregation/name event)))))))

(defmethod projection :congregation.event/permission-granted
  [state event]
  (let [cong-id (:congregation/id event)
        user-id (:user/id event)
        permission (:permission/id event)]
    (-> state
        (update-cong event (fn [congregation]
                             (-> congregation
                                 (update-in [:congregation/user-permissions user-id]
                                            conj-set permission))))
        (cond->
          (= :view-congregation permission) (update-in [::user->cong-ids user-id]
                                                       conj-set cong-id))
        (permissions/grant user-id [permission cong-id]))))

(defn- dissoc-empty [m k]
  (if (empty? (get m k))
    (dissoc m k)
    m))

(defmethod projection :congregation.event/permission-revoked
  [state event]
  (let [cong-id (:congregation/id event)
        user-id (:user/id event)
        permission (:permission/id event)]
    (-> state
        (update-cong event (fn [congregation]
                             (-> congregation
                                 (update-in [:congregation/user-permissions user-id]
                                            disj permission)
                                 (update :congregation/user-permissions
                                         dissoc-empty user-id))))
        (cond->
          (= :view-congregation permission) (update-in [::user->cong-ids user-id]
                                                       disj cong-id))
        (permissions/revoke user-id [permission cong-id]))))

(defn sudo [state user-id]
  (-> state
      (assoc-in [::user->cong-ids user-id]
                ;; no need to convert to set, because the state is transient
                (keys (::congregations state)))
      (permissions/grant user-id [:view-congregation])
      (permissions/grant user-id [:configure-congregation])))


;;;; Queries

(defn get-unrestricted-congregations [state]
  (vals (::congregations state)))

(defn get-unrestricted-congregation [state cong-id]
  (get (::congregations state) cong-id))

(defn- apply-user-permissions [cong state user-id]
  (when cong
    (when (permissions/allowed? state user-id [:view-congregation (:congregation/id cong)])
      cong)))

(defn get-my-congregation [state cong-id user-id]
  (-> (get-unrestricted-congregation state cong-id)
      (apply-user-permissions state user-id)))

(defn get-my-congregations [state user-id]
  (let [cong-ids (get-in state [::user->cong-ids user-id])]
    (map #(get-my-congregation state % user-id) cong-ids)))

(defn check-congregation-exists [state cong-id]
  (when-not (contains? (::congregations state) cong-id)
    (throw (ValidationException. [[:no-such-congregation cong-id]]))))


;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::congregations (:congregation/id command)])))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _congregation _injections]
                                      (:command/type command)))

(defn- admin-permissions-granted [cong-id user-id]
  (for [permission [:view-congregation :configure-congregation :gis-access]]
    {:event/type :congregation.event/permission-granted
     :event/version 1
     :congregation/id cong-id
     :user/id user-id
     :permission/id permission}))

(defmethod command-handler :congregation.command/create-congregation
  [command congregation {:keys [generate-tenant-schema-name]}]
  (let [cong-id (:congregation/id command)
        name (:congregation/name command)
        command-user (:command/user command)]
    (when (str/blank? name)
      (throw (ValidationException. [[:missing-name]])))
    (when (nil? (:congregation/id congregation)) ; idempotence
      (cons {:event/type :congregation.event/congregation-created
             :event/version 1
             :congregation/id cong-id
             :congregation/name name
             :congregation/schema-name (generate-tenant-schema-name cong-id)}
            ;; TODO: grant initial permissions using a process manager (after moving the events to a user stream)
            (when (some? command-user)
              (admin-permissions-granted cong-id command-user))))))

(defmethod command-handler :congregation.command/add-user
  [command congregation {:keys [check-permit]}]
  (let [cong-id (:congregation/id congregation)
        user-id (:user/id command)
        user-permissions (set (get-in congregation [:congregation/user-permissions user-id]))
        already-user? (contains? user-permissions :view-congregation)]
    (check-permit [:configure-congregation cong-id])
    (when-not already-user?
      ;; TODO: only grant :view-congregation to new users (after admin has a UI for editing permissions)
      (admin-permissions-granted cong-id user-id))))

(defmethod command-handler :congregation.command/set-user-permissions
  [command congregation {:keys [check-permit]}]
  (let [cong-id (:congregation/id congregation)
        user-id (:user/id command)
        old-permissions (set (get-in congregation [:congregation/user-permissions user-id]))
        new-permissions (set (:permission/ids command))
        added-permissions (set/difference new-permissions old-permissions)
        removed-permissions (set/difference old-permissions new-permissions)]
    (check-permit [:configure-congregation cong-id])
    (when (empty? old-permissions)
      (throw (ValidationException. [[:user-not-in-congregation user-id]])))
    (when-not (or (contains? new-permissions :view-congregation)
                  (empty? new-permissions))
      (throw (ValidationException. [[:cannot-revoke-view-congregation]])))
    (concat
     (for [added-permission (sort added-permissions)]
       {:event/type :congregation.event/permission-granted
        :event/version 1
        :congregation/id cong-id
        :user/id user-id
        :permission/id added-permission})
     (for [removed-permission (sort removed-permissions)]
       {:event/type :congregation.event/permission-revoked
        :event/version 1
        :congregation/id cong-id
        :user/id user-id
        :permission/id removed-permission}))))

(defmethod command-handler :congregation.command/rename-congregation
  [command congregation {:keys [check-permit]}]
  (let [cong-id (:congregation/id congregation)
        old-name (:congregation/name congregation)
        new-name (:congregation/name command)]
    (check-permit [:configure-congregation cong-id])
    (when (str/blank? new-name)
      (throw (ValidationException. [[:missing-name]])))
    (when-not (= old-name new-name)
      [{:event/type :congregation.event/congregation-renamed
        :event/version 1
        :congregation/id cong-id
        :congregation/name new-name}])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))


;;;; User access

(defn get-users [state cong-id]
  (let [cong (get-unrestricted-congregation state cong-id)]
    (keys (:congregation/user-permissions cong))))
