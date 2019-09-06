;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user
  (:require [clojure.java.jdbc :as jdbc]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events])
  (:import (java.security SecureRandom)
           (java.util Base64)
           (org.postgresql.util PSQLException)))

(defmulti ^:private update-congregation (fn [_congregation event] (:event/type event)))

(defmethod update-congregation :default [congregation _event]
  congregation)

(defmethod update-congregation :congregation.event/congregation-created
  [congregation event]
  (-> congregation
      (assoc :congregation/id (:congregation/id event))
      (assoc :congregation/schema-name (:congregation/schema-name event))))

(defn- update-permission [congregation event granted?]
  (if (= :gis-access (:permission/id event))
    (update-in congregation [:congregation/users (:user/id event)]
               (fn [user]
                 (-> user
                     (assoc :user/id (:user/id event))
                     (assoc :user/has-gis-access? granted?))))
    congregation))

(defmethod update-congregation :congregation.event/permission-granted
  [congregation event]
  (update-permission congregation event true))

(defmethod update-congregation :congregation.event/permission-revoked
  [congregation event]
  (update-permission congregation event false))

(defn- update-gis-user [congregation event desired-state]
  (update-in congregation [:congregation/users (:user/id event)]
             (fn [user]
               (-> user
                   (assoc :user/id (:user/id event))
                   (assoc :gis-user/desired-state desired-state)
                   (assoc :gis-user/username (:gis-user/username event))
                   (assoc :gis-user/password (:gis-user/password event))))))

(defmethod update-congregation :congregation.event/gis-user-created
  [congregation event]
  (update-gis-user congregation event :present))

(defmethod update-congregation :congregation.event/gis-user-deleted
  [congregation event]
  (update-gis-user congregation event :absent))


(defn- need-to-create? [state user-id cong-id]
  (let [user (get-in state [::congregations cong-id :congregation/users user-id])]
    (and (:user/has-gis-access? user)
         (not (:gis-user/password user)))))

(defn- need-to-delete? [state user-id cong-id]
  (let [user (get-in state [::congregations cong-id :congregation/users user-id])]
    (and (not (:user/has-gis-access? user))
         (:gis-user/password user))))

(defn- calculate-needed-changes [state event]
  (let [user-id (:user/id event)
        cong-id (:congregation/id event)
        account {:user/id user-id :congregation/id cong-id}]
    (-> state
        (update ::need-to-create
                (fn [needs]
                  (set (if (need-to-create? state user-id cong-id)
                         (conj needs account)
                         (disj needs account)))))
        (update ::need-to-delete
                (fn [needs]
                  (set (if (need-to-delete? state user-id cong-id)
                         (conj needs account)
                         (disj needs account))))))))


(defn gis-users-view [state event]
  (-> state
      (update-in [::congregations (:congregation/id event)] update-congregation event)
      (calculate-needed-changes event)))

(defn get-gis-users [state cong-id] ; TODO: remove me? only used in tests
  (->> (vals (get-in state [::congregations cong-id :congregation/users]))
       (filter (fn [user]
                 (= :present (:gis-user/desired-state user))))))

(defn get-gis-user [state cong-id user-id]
  (let [user (get-in state [::congregations cong-id :congregation/users user-id])]
    (when (= :present (:gis-user/desired-state user))
      user)))

(defn generate-password [length]
  (let [bytes (byte-array length)]
    (-> (SecureRandom/getInstanceStrong)
        (.nextBytes bytes))
    (-> (Base64/getUrlEncoder)
        (.encodeToString bytes)
        (.substring 0 length))))

(defn- uuid-prefix [uuid]
  (-> (str uuid)
      (.replace "-" "")
      (.substring 0 16)))

(defn ensure-present! [conn {:keys [username password schema]}]
  (assert username)
  (assert password)
  (assert schema)
  (try
    (jdbc/execute! conn [(str "CREATE ROLE " username " WITH LOGIN")])
    (catch PSQLException e
      ;; ignore if role exists
      (when (not= db/psql-duplicate-object (.getSQLState e))
        (throw e))))
  (jdbc/execute! conn [(str "ALTER ROLE " username " WITH PASSWORD '" password "'")])
  (jdbc/execute! conn [(str "ALTER ROLE " username " VALID UNTIL 'infinity'")])
  ;; TODO: move detailed permissions to schema specific role
  (jdbc/execute! conn [(str "GRANT USAGE ON SCHEMA " schema " TO " username)])
  (jdbc/execute! conn [(str "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE "
                            schema ".territory, "
                            schema ".congregation_boundary, "
                            schema ".subregion, "
                            schema ".card_minimap_viewport "
                            "TO " username)])
  nil)

(defn create-gis-user! [conn state cong-id user-id]
  ;; TODO: refactor to commands and process managers
  (let [username (str "gis_user_" (uuid-prefix cong-id) "_" (uuid-prefix user-id))
        password (generate-password 50)
        cong (congregation/get-unrestricted-congregation state cong-id)
        schema (:congregation/schema-name cong)]
    (assert schema)
    (event-store/save! conn cong-id nil
                       [(assoc (events/defaults)
                               :event/type :congregation.event/permission-granted
                               :congregation/id cong-id
                               :user/id user-id
                               :permission/id :gis-access)
                        (assoc (events/defaults)
                               :event/type :congregation.event/gis-user-created
                               :congregation/id cong-id
                               :user/id user-id
                               :gis-user/username username
                               :gis-user/password password)])
    (ensure-present! conn {:username username
                           :password password
                           :schema schema})))

(defn drop-role-cascade! [conn role schemas]
  (try
    (doseq [schema schemas]
      (jdbc/execute! conn [(str "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA " schema " FROM " role)])
      (jdbc/execute! conn [(str "REVOKE USAGE ON SCHEMA " schema " FROM " role)]))
    (catch PSQLException e
      ;; ignore if role does not exist
      (when (not= db/psql-undefined-object (.getSQLState e))
        (throw e))))
  (jdbc/execute! conn [(str "DROP ROLE IF EXISTS " role)])
  nil)

(defn ensure-absent! [conn {:keys [username schema]}]
  (drop-role-cascade! conn username [schema]))

(defn delete-gis-user! [conn state cong-id user-id]
  ;; TODO: refactor to commands and process managers
  (let [username (:gis-user/username (get-gis-user state cong-id user-id))
        cong (congregation/get-unrestricted-congregation state cong-id)
        schema (:congregation/schema-name cong)]
    (assert schema)
    (event-store/save! conn cong-id nil
                       [(assoc (events/defaults)
                               :event/type :congregation.event/permission-revoked
                               :congregation/id cong-id
                               :user/id user-id
                               :permission/id :gis-access)
                        (assoc (events/defaults)
                               :event/type :congregation.event/gis-user-deleted
                               :congregation/id cong-id
                               :user/id user-id
                               :gis-user/username username)])
    (ensure-absent! conn {:username username
                          :schema schema})))
