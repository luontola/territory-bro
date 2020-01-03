;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user
  (:require [medley.core :refer [dissoc-in]]
            [territory-bro.util :refer [conj-set]])
  (:import (java.security SecureRandom)
           (java.util Base64)
           (territory_bro ValidationException)))

;;; Read model

(defmulti gis-users-view (fn [_state event] (:event/type event)))

(defmethod gis-users-view :default
  [state _event]
  state)

(defmethod gis-users-view :congregation.event/gis-user-created
  [state event]
  (assoc-in state [::gis-users (:congregation/id event) (:user/id event)]
            (select-keys event [:user/id :gis-user/username :gis-user/password])))

(defmethod gis-users-view :congregation.event/gis-user-deleted
  [state event]
  (dissoc-in state [::gis-users (:congregation/id event) (:user/id event)]))


;;; Queries

(defn get-gis-users [state cong-id] ; TODO: remove me? only used in tests
  (vals (get-in state [::gis-users cong-id])))

(defn get-gis-user [state cong-id user-id]
  (get-in state [::gis-users cong-id user-id]))


;;; Write model

(defn- username-path [event-or-command]
  [::usernames-by-cong-user (select-keys event-or-command [:congregation/id :user/id])])

(defmulti ^:private write-model (fn [_state event] (:event/type event)))

(defmethod write-model :default
  [state _event]
  state)

(defmethod write-model :congregation.event/gis-user-created
  [state event]
  (-> state
      ;; TODO: reuse the read model?
      (assoc-in (username-path event) (:gis-user/username event))))

(defmethod write-model :congregation.event/gis-user-deleted
  [state event]
  (-> state
      ;; TODO: reuse the read model?
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

(defmulti ^:private command-handler (fn [command _state _injections] (:command/type command)))

(defmethod command-handler :gis-user.command/create-gis-user [command state {:keys [generate-password db-user-exists? check-permit check-congregation-exists check-user-exists]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:create-gis-user cong-id user-id])
    (check-congregation-exists cong-id)
    (check-user-exists user-id)
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

(defmethod command-handler :gis-user.command/delete-gis-user [command state {:keys [check-permit check-congregation-exists check-user-exists]}]
  (let [cong-id (:congregation/id command)
        user-id (:user/id command)]
    (check-permit [:delete-gis-user cong-id user-id])
    (check-congregation-exists cong-id)
    (check-user-exists user-id)
    (when-some [username (get-in state (username-path command))]
      [{:event/type :congregation.event/gis-user-deleted
        :event/version 1
        :congregation/id cong-id
        :user/id user-id
        :gis-user/username username}])))

(defn handle-command [command events injections]
  (let [state (reduce write-model nil events)]
    (command-handler command state injections)))
