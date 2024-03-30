;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.gis-user
  (:require [medley.core :refer [dissoc-in]])
  (:import (java.security SecureRandom)
           (java.util Base64)))

;;;; Read model

(defmulti projection (fn [_state event] (:event/type event)))

(defmethod projection :default
  [state _event]
  state)

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (assoc-in state [::gis-users (:congregation/id event) (:user/id event)]
            (select-keys event [:user/id :gis-user/username :gis-user/password])))

(defmethod projection :congregation.event/gis-user-deleted
  [state event]
  (dissoc-in state [::gis-users (:congregation/id event) (:user/id event)]))


;;;; Queries

(defn get-gis-user [state cong-id user-id]
  (get-in state [::gis-users cong-id user-id]))


;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::gis-users (:congregation/id command) (:user/id command)])))


;;;; Command Handlers

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

(defmethod command-handler :gis-user.command/create-gis-user [command gis-user {:keys [generate-password db-user-exists? check-permit]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:create-gis-user cong-id user-id])
    (when (nil? gis-user)
      [{:event/type :congregation.event/gis-user-created
        :congregation/id cong-id
        :user/id user-id
        ;; Due to PostgreSQL identifier length limits, the username is based on
        ;; truncated UUIDs and is not guaranteed to be unique as-is.
        :gis-user/username (-> (generate-username cong-id user-id)
                               (unique-username db-user-exists?))
        :gis-user/password (generate-password)}])))

(defmethod command-handler :gis-user.command/delete-gis-user [command gis-user {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:delete-gis-user cong-id user-id])
    (when (some? gis-user)
      [{:event/type :congregation.event/gis-user-deleted
        :congregation/id cong-id
        :user/id user-id
        :gis-user/username (:gis-user/username gis-user)}])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
