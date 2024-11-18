(ns territory-bro.domain.share
  (:require [clojure.string :as str]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.permissions :as permissions])
  (:import (java.net URLEncoder)
           (java.nio ByteBuffer)
           (java.nio.charset StandardCharsets)
           (java.security SecureRandom)
           (java.util Base64 UUID)
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
                                                                 :share/type
                                                                 :congregation/id
                                                                 :territory/id]))))

(defmethod projection :share.event/share-opened
  [state event]
  (-> state
      (assoc-in [::shares (:share/id event) :share/last-opened] (:event/time event))))

(defn- grant-opened-share [state share-id user-id]
  (let [share (get-in state [::shares share-id])
        cong-id (:congregation/id share)]
    (if (and (some? share)
             (not (permissions/allowed? state user-id [:view-congregation cong-id])))
      (-> state
          (permissions/grant user-id [:view-congregation-temporarily cong-id])
          (permissions/grant user-id [:view-territory cong-id (:territory/id share)]))
      state)))

(defn grant-opened-shares [state share-ids user-id]
  (reduce #(grant-opened-share %1 %2 user-id) state share-ids))


;;;; Queries

(defn get-share [state share-id]
  (get-in state [::shares share-id]))

(defn share-exists? [state share-id]
  (some? (get-share state share-id)))

(defn check-share-exists [state share-id]
  (when-not (share-exists? state share-id)
    (throw (ValidationException. [[:no-such-share share-id]]))))

(defn find-share-by-key [state share-key]
  (let [share-id (get-in state [::share-keys share-key])
        share (get-in state [::shares share-id])]
    share))


;;;; Command handlers

(def ^:private ^SecureRandom random (SecureRandom.))

(defn- random-bytes ^bytes [len]
  (let [bs (make-array Byte/TYPE len)]
    (.nextBytes random bs)
    bs))

(defn- bytes->share-key [^bytes bs]
  (-> (.encodeToString (Base64/getUrlEncoder) bs)
      (StringUtils/stripEnd "=")))

(defn ^:dynamic generate-share-key []
  ;; generate a 64-bit key, base64 encoded; same format as YouTube video IDs
  (-> (random-bytes 8)
      (bytes->share-key)))

(defn build-share-url [share-key territory-number]
  (let [safe-number (-> (str territory-number)
                        (URLEncoder/encode StandardCharsets/UTF_8)
                        (str/replace #"%.." "_")
                        (str/replace "+" "_")
                        (str/replace #"_+" "_"))]
    (str (:public-url config/env) "/share/" share-key "/" safe-number)))

(defn build-qr-code-url [share-key]
  (str (:qr-code-base-url config/env) "/" share-key))


(defn- uuid->bytes ^bytes [^UUID uuid]
  (let [bs (ByteBuffer/allocate 16)]
    (.putLong bs (.getMostSignificantBits uuid))
    (.putLong bs (.getLeastSignificantBits uuid))
    (.array bs)))

(defn- bytes->uuid ^UUID [^bytes bs]
  (when (= 16 (count bs))
    (let [bs (ByteBuffer/wrap bs)]
      (UUID. (.getLong bs) (.getLong bs)))))

(def demo-share-key-prefix "demo-")

(defn demo-share-key [^UUID territory-id]
  (str demo-share-key-prefix
       (-> territory-id
           (uuid->bytes)
           (bytes->share-key))))

(defn demo-share-key->territory-id [key]
  (when (and (string? key)
             (str/starts-with? key demo-share-key-prefix))
    (let [base64 (subs key (count demo-share-key-prefix))]
      (try
        (-> (.decode (Base64/getUrlDecoder) base64)
            (bytes->uuid))
        (catch IllegalArgumentException _ ; base64 decoding failed
          nil)))))


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
