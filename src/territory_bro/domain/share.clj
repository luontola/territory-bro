;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.share
  (:import (java.security SecureRandom)
           (java.util Base64)
           (org.apache.commons.lang3 StringUtils)
           (territory_bro WriteConflictException)))

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


;;;; Queries

(defn find-share-by-key [state share-key]
  (let [share-id (get-in state [::share-keys share-key])
        share (get-in state [::shares share-id])]
    share))

;;;; Write model

(defn- write-model [command events]
  (let [state (reduce projection nil events)]
    (get-in state [::shares (:share/id command)])))


;;;; Command handlers

(def ^:private ^SecureRandom random (SecureRandom.))

(defn generate-share-key []
  ;; generate a 64-bit key, base64 encoded; same format as YouTube video IDs
  (let [bs (make-array Byte/TYPE 8)]
    (.nextBytes random bs)
    (-> (.encodeToString (Base64/getUrlEncoder) bs)
        (StringUtils/stripEnd "="))))

(defmulti ^:private command-handler (fn [command _share _injections]
                                      (:command/type command)))

(defmethod command-handler :share.command/share-territory-link
  [command share {:keys [check-permit state]}]
  (let [share-id (:share/id command)
        share-key (:share/key command)
        cong-id (:congregation/id command)
        territory-id (:territory/id command)
        key-owner (get-in state [::share-keys share-key])]
    (check-permit [:share-territory-link cong-id territory-id])
    (when (and (some? key-owner)
               (not= share-id key-owner))
      (throw (WriteConflictException. (str "share key " share-key " already in use by share " key-owner))))
    (when (nil? share)
      [{:event/type :share.event/share-created
        :share/id share-id
        :share/key share-key
        :share/type :link
        :congregation/id cong-id
        :territory/id territory-id}])))

(defn handle-command [command events injections]
  (command-handler command (write-model command events) injections))
