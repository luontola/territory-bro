;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [medley.core :refer [dissoc-in]]
            [territory-bro.db :as db]
            [territory-bro.util :refer [conj-set]])
  (:import (java.security SecureRandom)
           (java.util Base64)
           (org.postgresql.util PSQLException)
           (territory_bro ValidationException)))

;;; Read model

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


;;; Write model

(defn- username-path [event-or-command]
  [::usernames-by-cong-user (select-keys event-or-command [:congregation/id :user/id])])

(defmulti ^:private write-model (fn [_state event] (:event/type event)))

(defmethod write-model :default
  [state _event]
  state)

(defmethod write-model :congregation.event/congregation-created
  [state event]
  (-> state
      (update ::congregations conj-set (:congregation/id event))))

(defmethod write-model :congregation.event/permission-granted
  [state event]
  (-> state
      (update ::users conj-set (:user/id event))))

(defmethod write-model :congregation.event/gis-user-created
  [state event]
  (-> state
      (assoc-in (username-path event) (:gis-user/username event))))

(defmethod write-model :congregation.event/gis-user-deleted
  [state event]
  (-> state
      (dissoc-in (username-path event))))


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

(defn- check-congregation-exists [state cong-id]
  (when-not (contains? (::congregations state) cong-id)
    (throw (ValidationException. [[:no-such-congregation cong-id]]))))

(defn- check-user-exists [state user-id]
  (when-not (contains? (::users state) user-id)
    (throw (ValidationException. [[:no-such-user user-id]]))))

(defmulti ^:private command-handler (fn [command _state _injections] (:command/type command)))

(defmethod command-handler :gis-user.command/create-gis-user [command state {:keys [generate-password db-user-exists? check-permit]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:create-gis-user cong-id user-id])
    (check-congregation-exists state cong-id)
    (check-user-exists state user-id)
    (when (nil? (get-in state (username-path command)))
      [{:event/type :congregation.event/gis-user-created
        :event/version 1
        :congregation/id cong-id
        :user/id user-id
        ;; Due to PostgreSQL identifier length limits, the username is based on
        ;; truncated UUIDs and is not guaranteed to be unique as-is.
        :gis-user/username (-> (generate-username cong-id user-id)
                               (unique-username db-user-exists?))
        :gis-user/password (generate-password)}])))

(defmethod command-handler :gis-user.command/delete-gis-user [command state {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:delete-gis-user cong-id user-id])
    (check-congregation-exists state cong-id)
    (check-user-exists state user-id)
    (when-some [username (get-in state (username-path command))]
      [{:event/type :congregation.event/gis-user-deleted
        :event/version 1
        :congregation/id cong-id
        :user/id user-id
        :gis-user/username username}])))

(defn handle-command [command events injections]
  (let [state (reduce write-model nil events)]
    (command-handler command state injections)))
