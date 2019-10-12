;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [territory-bro.commands :as commands]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events]
            [territory-bro.permissions :as permissions]
            [territory-bro.user :as user])
  (:import (java.util UUID)
           (territory_bro ValidationException)))

;;; Read model

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
                 (fnil conj #{})
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

(defn get-unrestricted-congregations [state]
  (vals (::congregations state)))

(defn get-unrestricted-congregation [state cong-id]
  (get (::congregations state) cong-id))

(defn- apply-user-permissions [cong user-id]
  (let [permissions (get-in cong [:congregation/user-permissions user-id])]
    (when (contains? permissions :view-congregation)
      cong)))

(defn get-my-congregations [state user-id]
  ;; TODO: avoid the linear search
  (->> (get-unrestricted-congregations state)
       (map #(apply-user-permissions % user-id))
       (remove nil?)))

(defn get-my-congregation [state cong-id user-id]
  (-> (get-unrestricted-congregation state cong-id)
      (apply-user-permissions user-id)))

(defn use-schema [conn state cong-id] ; TODO: create a better helper?
  (let [cong (get-unrestricted-congregation state cong-id)
        schema (:congregation/schema-name cong)]
    (db/use-tenant-schema conn schema)))

(defn create-congregation! [conn name]
  (let [id (UUID/randomUUID)
        master-schema (:database-schema config/env)
        tenant-schema (str master-schema
                           "_"
                           (str/replace (str id) "-" ""))]
    (assert (not (contains? (set (db/get-schemas conn))
                            tenant-schema))
            {:schema-name tenant-schema})
    (event-store/save! conn id 0 [(assoc (events/defaults)
                                         :event/type :congregation.event/congregation-created
                                         :congregation/id id
                                         :congregation/name name
                                         :congregation/schema-name tenant-schema)])
    (-> (db/tenant-schema tenant-schema master-schema)
        (.migrate))
    (log/info "Congregation created:" id)
    id))


;;;; Write model

(defmulti ^:private write-model (fn [_congregation event]
                                  (:event/type event)))

(defmethod write-model :default
  [congregation _event]
  congregation)

(defmethod write-model :congregation.event/congregation-created
  [congregation event]
  (-> congregation
      (assoc :congregation/id (:congregation/id event))
      (assoc :congregation/name (:congregation/name event))))

(defmethod write-model :congregation.event/congregation-renamed
  [congregation event]
  (-> congregation
      (assoc :congregation/name (:congregation/name event))))

(defmethod write-model :congregation.event/permission-granted
  [congregation event]
  (if (= :view-congregation (:permission/id event))
    (update congregation ::users (fnil conj #{}) (:user/id event))
    congregation))

(defmethod write-model :congregation.event/permission-revoked
  [congregation event]
  (if (= :view-congregation (:permission/id event))
    (update congregation ::users disj (:user/id event))
    congregation))


(defmulti ^:private command-handler (fn [command _congregation _injections]
                                      (:command/type command)))

(defmethod command-handler :congregation.command/add-user
  [command congregation {:keys [user-exists? check-permit]}]
  (let [cong-id (:congregation/id congregation)
        user-id (:user/id command)]
    (check-permit [:configure-congregation cong-id])
    (when-not (user-exists? user-id)
      (throw (ValidationException. [[:no-such-user user-id]])))
    (when-not (contains? (::users congregation) user-id)
      [{:event/type :congregation.event/permission-granted
        :event/version 1
        :congregation/id cong-id
        :user/id user-id
        :permission/id :view-congregation}])))

(defmethod command-handler :congregation.command/rename-congregation
  [command congregation {:keys [check-permit]}]
  (check-permit [:configure-congregation (:congregation/id congregation)])
  (when-not (= (:congregation/name congregation)
               (:congregation/name command))
    [{:event/type :congregation.event/congregation-renamed
      :event/version 1
      :congregation/id (:congregation/id congregation)
      :congregation/name (:congregation/name command)}]))


(defn- enrich-event [event command]
  (-> event
      (assoc :event/time (:command/time command))
      (assoc :event/user (:command/user command))))

(defn handle-command [command events injections]
  (let [command (commands/validate-command command)
        congregation (reduce write-model nil events)
        injections (merge {:check-permit (fn [permit]
                                           (permissions/check (:state injections)
                                                              (:command/user command)
                                                              permit))
                           :user-exists? (fn [user-id]
                                           (db/with-db [conn {:read-only? true}]
                                             (some? (user/get-by-id conn user-id))))}
                          injections)]
    (->> (command-handler command congregation injections)
         (map #(enrich-event % command)))))

(defn command! [conn state command]
  (let [stream-id (:congregation/id command)
        old-events (event-store/read-stream conn stream-id)
        new-events (handle-command command old-events {:state state})]
    (event-store/save! conn stream-id (count old-events) new-events)))


;;;; User access

(defn get-users [state cong-id]
  (let [cong (get-unrestricted-congregation state cong-id)]
    (->> (:congregation/user-permissions cong)
         ;; TODO: remove old users already in the projection
         (filter (fn [[_user-id permissions]]
                   (not (empty? permissions))))
         (keys))))

(defn grant! [conn cong-id user-id permission]
  ;; TODO: refactor to event sourcing commands
  (event-store/save! conn cong-id nil
                     [(assoc (events/defaults)
                             :event/type :congregation.event/permission-granted
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/id permission)])
  nil)

(defn revoke! [conn cong-id user-id permission]
  ;; TODO: refactor to event sourcing commands
  (event-store/save! conn cong-id nil
                     [(assoc (events/defaults)
                             :event/type :congregation.event/permission-revoked
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/id permission)])
  nil)
