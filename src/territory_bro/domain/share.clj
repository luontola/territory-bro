;; Copyright © 2015-2023 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.share
  (:require [territory-bro.infra.permissions :as permissions])
  (:import (java.security SecureRandom)
           (java.util Base64)
           (org.apache.commons.lang3 StringUtils)
           (territory_bro ValidationException WriteConflictException)))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defmethod projection :share.event/share-created
  [state event]
  (-> state
      (assoc-in [::share-keys (:share/key event)] (:share/id event))
      (assoc-in [::shares (:share/id event)] (select-keys event [:share/id
                                                                 :congregation/id
                                                                 :territory/id]))))

(defmethod projection :share.event/share-opened
  [state event]
  (-> state
      (assoc-in [::shares (:share/id event) :share/last-opened] (:event/time event))))

(defn- grant-opened-share [state share-id user-id]
  (if-some [share (get-in state [::shares share-id])]
    (permissions/grant state user-id [:view-territory (:congregation/id share) (:territory/id share)])
    state))

(defn grant-opened-shares [state share-ids user-id]
  (reduce #(grant-opened-share %1 %2 user-id) state share-ids))


;;;; Queries

(defn share-exists? [state share-id]
  (some? (get-in state [::shares share-id])))

(defn check-share-exists [state share-id]
  (when-not (share-exists? state share-id)
    (throw (ValidationException. [[:no-such-share share-id]]))))

(defn find-share-by-key [state share-key]
  (let [share-id (get-in state [::share-keys share-key])
        share (get-in state [::shares share-id])]
    share))


;;;; Command handlers

(def ^:private ^SecureRandom random (SecureRandom.))

(defn ^:dynamic generate-share-key []
  ;; generate a 64-bit key, base64 encoded; same format as YouTube video IDs
  (let [bs (make-array Byte/TYPE 8)]
    (.nextBytes random bs)
    (-> (.encodeToString (Base64/getUrlEncoder) bs)
        (StringUtils/stripEnd "="))))

(defmulti ^:private command-handler (fn [command _state _injections]
                                      (:command/type command)))

(defmethod command-handler :share.command/create-share
  [command state {:keys [check-permit]}]
  (let [share-id (:share/id command)
        share-key (:share/key command)
        share-type (:share/type command)
        cong-id (:congregation/id command)
        territory-id (:territory/id command)
        key-owner (get-in state [::share-keys share-key])]
    (check-permit [:share-territory-link cong-id territory-id])
    (when (and (some? key-owner)
               (not= share-id key-owner))
      (throw (WriteConflictException. (str "share key " share-key " already in use by share " key-owner))))
    (when-not (share-exists? state share-id)
      [{:event/type :share.event/share-created
        :share/id share-id
        :share/key share-key
        :share/type share-type
        :congregation/id cong-id
        :territory/id territory-id}])))

(defmethod command-handler :share.command/record-share-opened
  [command _state _injections]
  [{:event/type :share.event/share-opened
    :share/id (:share/id command)}])

(defn handle-command [command state injections]
  (command-handler command state injections))
