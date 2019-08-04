;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer [deftest is]]
            [mount.core :as mount]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
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
  (update congregations (:congregation/id event) update-congregation event))


(def ^:private query! (db/compile-queries "db/hugsql/gis-user.sql"))

(defn get-gis-users [state cong-id] ; TODO: remove me? only used in tests
  (->> (vals (get-in state [cong-id :congregation/users]))
       (filter (fn [user]
                 (= :present (:gis-user/desired-state user))))))

(defn get-gis-user [state cong-id user-id]
  (let [user (get-in state [cong-id :congregation/users user-id])]
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

(defn create-gis-user! [conn cong-id user-id]
  ;; TODO: refactor to commands and process managers
  (let [username (str "gis_user_" (uuid-prefix cong-id) "_" (uuid-prefix user-id))
        password (generate-password 50)
        schema (get-in (congregation/current-state conn) [cong-id :congregation/schema-name])]
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
    (query! conn :create-gis-user {:congregation cong-id
                                   :user user-id
                                   :username username
                                   :password password})
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

(declare current-state)
(defn delete-gis-user! [conn cong-id user-id]
  ;; TODO: refactor to commands and process managers
  (let [username (:gis-user/username (get-gis-user (current-state conn) cong-id user-id))
        schema (get-in (congregation/current-state conn) [cong-id :congregation/schema-name])]
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
    (query! conn :delete-gis-user {:congregation cong-id
                                   :user user-id})
    nil))

;; TODO: deduplicate with congregation namespace

(mount/defstate cache
  :start (atom {:last-event nil
                :state nil}))

(defn- apply-new-events [conn cached]
  (let [new-events (event-store/read-all-events conn {:since (:event/global-revision (:last-event cached))})
        last-event (last new-events)]
    (if last-event
      {:last-event last-event
       :state (reduce gis-users-view (:state cached) new-events)}
      cached)))

(defn update-cache! [conn]
  (let [cached @cache
        updated (apply-new-events conn cached)]
    (when-not (identical? cached updated)
      ;; with concurrent requests, only one of them will update the cache
      (compare-and-set! cache cached updated))))

(defn current-state
  "Calculates the current state from all events, including uncommitted ones,
   but does not update the cache (it could cause dirty reads to others)."
  [conn]
  (:state (apply-new-events conn @cache)))

(comment
  (count (:state @cache))
  (update-cache! db/database))
