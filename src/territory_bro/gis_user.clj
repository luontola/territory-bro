;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user
  (:require [clojure.java.jdbc :as jdbc]
            [territory-bro.congregation :as congregation]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events])
  (:import (java.security SecureRandom)
           (java.util Base64)))

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

(defn gis-users-view [congregations event]
  (update-in congregations [::congregations (:congregation/id event)] update-congregation event))

(defn get-gis-users [state cong-id] ; TODO: remove me? only used in tests
  (->> (vals (get-in state [::congregations cong-id :congregation/users]))
       (filter (fn [user]
                 (= :present (:gis-user/desired-state user))))))

(defn get-gis-user [state cong-id user-id]
  (let [user (get-in state [::congregations cong-id :congregation/users user-id])]
    (when (= :present (:gis-user/desired-state user))
      user)))

(defn gis-users-to-create [state]
  (set (for [[cong-id cong] (::congregations state)
             [user-id user] (:congregation/users cong)
             :when (and (:user/has-gis-access? user)
                        (not (:gis-user/password user)))]
         {:congregation/id cong-id
          :user/id user-id})))

(defn gis-users-to-delete [state]
  (set (for [[cong-id cong] (::congregations state)
             [user-id user] (:congregation/users cong)
             :when (and (not (:user/has-gis-access? user))
                        (:gis-user/password user))]
         {:congregation/id cong-id
          :user/id user-id})))

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
    (jdbc/execute! conn [(str "CREATE ROLE " username " WITH LOGIN")])
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
    nil))

(defn drop-role-cascade! [conn role schemas]
  (doseq [schema schemas]
    (jdbc/execute! conn [(str "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA " schema " FROM " role)])
    (jdbc/execute! conn [(str "REVOKE USAGE ON SCHEMA " schema " FROM " role)]))
  (jdbc/execute! conn [(str "DROP ROLE " role)]))

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
    (drop-role-cascade! conn username [schema])
    nil))
