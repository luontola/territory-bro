;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user
  (:require [clojure.java.jdbc :as jdbc]
            [medley.core :refer [dissoc-in]]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events])
  (:import (java.security SecureRandom)
           (java.util Base64)
           (org.postgresql.util PSQLException)))

;;;  Read Model

(defmulti ^:private update-congregation (fn [_congregation event] (:event/type event)))

(defmethod update-congregation :default
  [congregation _event]
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


;;; Write Model

(defn- username-path [event-or-command]
  [::usernames-by-cong-user (select-keys event-or-command [:congregation/id :user/id])])

(defmulti ^:private write-model (fn [_state event] (:event/type event)))

(defmethod write-model :default
  [state _event]
  state)

(defmethod write-model :congregation.event/gis-user-created
  [state event]
  (assoc-in state (username-path event) (:gis-user/username event)))

(defmethod write-model :congregation.event/gis-user-deleted
  [state event]
  (dissoc-in state (username-path event)))


;; Command Handlers

(defn- uuid-prefix [uuid]
  (-> (str uuid)
      (.replace "-" "")
      (.substring 0 16)))

(defn- generate-username [cong-id user-id]
  (str "gis_user_" (uuid-prefix cong-id) "_" (uuid-prefix user-id)))

(defn- unique-username [username db-user-exists?]
  (->> (cons username (map #(str username "_" %) (iterate inc 1)))
       (remove db-user-exists?)
       (first)))

(defn generate-password [length]
  (let [bytes (byte-array length)]
    (-> (SecureRandom/getInstanceStrong)
        (.nextBytes bytes))
    (-> (Base64/getUrlEncoder)
        (.encodeToString bytes)
        (.substring 0 length))))

(defmulti ^:private command-handler (fn [command _state _injections] (:command/type command)))

(defmethod command-handler :gis-user.command/create-gis-user [command state {:keys [generate-password db-user-exists?]}]
  (when (nil? (get-in state (username-path command)))
    [{:event/type :congregation.event/gis-user-created
      :event/version 1
      :event/time (:command/time command)
      :event/user (:command/user command)
      :congregation/id (:congregation/id command)
      :user/id (:user/id command)
      ;; Due to PostgreSQL identifier length limits, the username is based on
      ;; truncated UUIDs and is not guaranteed to be unique as-is.
      :gis-user/username (-> (generate-username (:congregation/id command) (:user/id command))
                             (unique-username db-user-exists?))
      :gis-user/password (generate-password)}]))

(defmethod command-handler :gis-user.command/delete-gis-user [command state _injections]
  (when-some [username (get-in state (username-path command))]
    [{:event/type :congregation.event/gis-user-deleted
      :event/version 1
      :event/time (:command/time command)
      :event/user (:command/user command)
      :congregation/id (:congregation/id command)
      :user/id (:user/id command)
      :gis-user/username username}]))

(defn handle-command [command events injections]
  (let [state (reduce write-model nil events)]
    (command-handler command state injections)))

(defn command! [conn command]
  ;; TODO: the GIS user events would belong better to a user-specific stream
  (let [stream-id (:congregation/id command)
        old-events (event-store/read-stream conn stream-id)
        new-events (handle-command command old-events {})]
    (event-store/save! conn stream-id (count old-events) new-events)))


;;; Database Users

(defn ensure-present! [conn {:keys [username password schema]}]
  (assert username)
  (assert password)
  (assert schema)
  (try
    (jdbc/execute! conn ["SAVEPOINT create_role"])
    (jdbc/execute! conn [(str "CREATE ROLE " username " WITH LOGIN")])
    (jdbc/execute! conn ["RELEASE SAVEPOINT create_role"])
    (catch PSQLException e
      ;; ignore error if role already exists
      (if (= db/psql-duplicate-object (.getSQLState e))
        (jdbc/execute! conn ["ROLLBACK TO SAVEPOINT create_role"])
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
  (let [username (generate-username cong-id user-id)
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
  (assert role)
  (try
    (doseq [schema schemas]
      (assert schema)
      (jdbc/execute! conn ["SAVEPOINT revoke_privileges"])
      (jdbc/execute! conn [(str "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA " schema " FROM " role)])
      (jdbc/execute! conn [(str "REVOKE USAGE ON SCHEMA " schema " FROM " role)])
      (jdbc/execute! conn ["RELEASE SAVEPOINT revoke_privileges"]))
    (catch PSQLException e
      ;; ignore error if role already does not exist
      (if (= db/psql-undefined-object (.getSQLState e))
        (jdbc/execute! conn ["ROLLBACK TO SAVEPOINT revoke_privileges"])
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
