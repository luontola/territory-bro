;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events])
  (:import (java.util UUID)))

(defmulti ^:private update-congregation (fn [_congregation event] (:event/type event)))

(defmethod update-congregation :default [congregation _event]
  congregation)

(defmethod update-congregation :congregation.event/congregation-created
  [congregation event]
  (-> congregation
      (assoc :congregation/id (:congregation/id event))
      (assoc :congregation/name (:congregation/name event))
      (assoc :congregation/schema-name (:congregation/schema-name event))))

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

(defn congregations-view [state event]
  (update-in state [::congregations (:congregation/id event)] update-congregation event))

(defn get-my-congregations [state user-id]
  (->> (::congregations state)
       (filter (fn [[_cong-id cong]]
                 (let [permissions (get-in cong [:congregation/user-permissions user-id])]
                   (contains? permissions :view-congregation))))))

(defn get-my-congregation [state cong-id user-id]
  (let [cong (get-in state [::congregations cong-id])
        permissions (get-in cong [:congregation/user-permissions user-id])]
    (when (contains? permissions :view-congregation)
      cong)))

(defn use-schema [conn state cong-id] ; TODO: create a better helper?
  (let [schema (get-in state [::congregations cong-id :congregation/schema-name])]
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

;;;; User access

(defn get-users [state cong-id]
  (let [cong (get-in state [::congregations cong-id])]
    (->> (:congregation/user-permissions cong)
         ;; TODO: remove old users already in the projection
         (filter (fn [[_user-id permissions]]
                   (not (empty? permissions))))
         (keys))))

(defn grant-access! [conn cong-id user-id]
  (event-store/save! conn cong-id nil
                     [(assoc (events/defaults)
                             :event/type :congregation.event/permission-granted
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/id :view-congregation)])
  nil)

(defn revoke-access! [conn cong-id user-id]
  (event-store/save! conn cong-id nil
                     [(assoc (events/defaults)
                             :event/type :congregation.event/permission-revoked
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/id :view-congregation)])
  nil)
