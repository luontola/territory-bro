;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [territory-bro.permissions :as permissions]
            [territory-bro.util :refer [conj-set]])
  (:import (territory_bro ValidationException)))

;;;; Read model

(defmulti ^:private update-congregation (fn [_congregation event]
                                          (:event/type event)))

(defmethod update-congregation :default [congregation _event]
  congregation)

(defmethod update-congregation :congregation.event/congregation-created
  [congregation event]
  (-> congregation
      (assoc :congregation/id (:congregation/id event))
      (assoc :congregation/name (:congregation/name event))
      (assoc :congregation/schema-name (:congregation/schema-name event))))

(defmethod update-congregation :congregation.event/congregation-renamed
  [congregation event]
  (-> congregation
      (assoc :congregation/name (:congregation/name event))))

(defmethod update-congregation :congregation.event/permission-granted
  [congregation event]
  (-> congregation
      (update-in [:congregation/user-permissions (:user/id event)]
                 conj-set
                 (:permission/id event))))

(defmethod update-congregation :congregation.event/permission-revoked
  [congregation event]
  (-> congregation
      ;; TODO: remove user when no more permissions remain
      (update-in [:congregation/user-permissions (:user/id event)]
                 disj
                 (:permission/id event))))


(defmulti ^:private update-permissions (fn [_state event]
                                         (:event/type event)))

(defmethod update-permissions :default [state _event]
  state)

(defmethod update-permissions :congregation.event/permission-granted
  [state event]
  (-> state
      (permissions/grant (:user/id event) [(:permission/id event)
                                           (:congregation/id event)])))

(defmethod update-permissions :congregation.event/permission-revoked
  [state event]
  (-> state
      (permissions/revoke (:user/id event) [(:permission/id event)
                                            (:congregation/id event)])))


(defn congregations-view [state event]
  (-> state
      (update-in [::congregations (:congregation/id event)] update-congregation event)
      (update-permissions event)))


;;;; Queries

(defn get-unrestricted-congregations [state]
  (vals (::congregations state)))

(defn get-unrestricted-congregation [state cong-id]
  (get (::congregations state) cong-id))

(defn- apply-user-permissions [cong state user-id]
  (when cong
    (when (permissions/allowed? state user-id [:view-congregation (:congregation/id cong)])
      cong)))

(defn get-my-congregations [state user-id]
  ;; TODO: avoid the linear search
  (->> (get-unrestricted-congregations state)
       (map #(apply-user-permissions % state user-id))
       (remove nil?)))

(defn get-my-congregation [state cong-id user-id]
  (-> (get-unrestricted-congregation state cong-id)
      (apply-user-permissions state user-id)))

(defn check-congregation-exists [state cong-id]
  (when-not (contains? (::congregations state) cong-id)
    (throw (ValidationException. [[:no-such-congregation cong-id]]))))


;;;; Write model

(defn- write-model [congregation event]
  (-> congregation
      (update-congregation event)))


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
        user-id (:command/user command)
        name (:congregation/name command)]
    (when (str/blank? name)
      (throw (ValidationException. [[:missing-name]])))
    (when (nil? (:congregation/id congregation)) ; idempotence
      (cons {:event/type :congregation.event/congregation-created
             :event/version 1
             :congregation/id cong-id
             :congregation/name name
             :congregation/schema-name (generate-tenant-schema-name cong-id)}
            ;; TODO: grant initial permissions using a process manager (after moving the events to a user stream)
            (admin-permissions-granted cong-id user-id)))))

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
    ;; TODO: remove :view-congregation only if removing all permissions
    ;; TODO: don't allow adding new users with this command?
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
  (let [congregation (reduce write-model nil events)]
    (command-handler command congregation injections)))


;;;; User access

(defn get-users [state cong-id]
  (let [cong (get-unrestricted-congregation state cong-id)]
    (->> (:congregation/user-permissions cong)
         ;; TODO: remove old users already in the projection
         (filter (fn [[_user-id permissions]]
                   (not (empty? permissions))))
         (keys))))
