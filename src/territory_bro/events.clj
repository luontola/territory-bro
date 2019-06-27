;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.events
  (:require [schema-refined.core :as refined]
            [schema-tools.core :as tools]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [schema.utils]
            [territory-bro.json :as json])
  (:import (java.time Instant)
           (java.util UUID)))

(def ^:dynamic *current-time* nil)
(def ^:dynamic *current-user* nil)
(def ^:dynamic *current-system* nil)

(defn defaults
  ([]
   (defaults (or *current-time* (Instant/now))))
  ([^Instant now]
   (cond-> {:event/version 1
            :event/time now}
     *current-user* (assoc :event/user *current-user*)
     *current-system* (assoc :event/system *current-system*))))

(def ^:private key-order
  (->> [:event/type
        :event/version
        :event/user
        :event/system
        :event/time
        :event/stream-id
        :event/stream-revision
        :event/global-revision]
       (map-indexed (fn [idx k]
                      [k idx]))
       (into {})))

(defn- key-comparator [x y]
  (compare [(get key-order x 100) x]
           [(get key-order y 100) y]))

(defn sorted-keys [event]
  (into (sorted-map-by key-comparator)
        event))

(s/defschema EventBase
  {(s/optional-key :event/stream-id) UUID
   (s/optional-key :event/stream-revision) s/Int
   (s/optional-key :event/global-revision) s/Int
   :event/type s/Keyword
   :event/version s/Int
   :event/time Instant
   (s/optional-key :event/user) UUID
   (s/optional-key :event/system) s/Str})

(s/defschema CongregationCreated
  (assoc EventBase
         :event/type (s/eq :congregation.event/congregation-created)
         :event/version (s/eq 1)
         :congregation/id UUID
         :congregation/name s/Str
         :congregation/schema-name s/Str))

(s/defschema PermissionId
  (s/enum :view-congregation
          :gis-access))
(s/defschema PermissionGranted
  (assoc EventBase
         :event/type (s/eq :congregation.event/permission-granted)
         :event/version (s/eq 1)
         :congregation/id UUID
         :user/id UUID
         :permission/id PermissionId))
(s/defschema PermissionRevoked
  (assoc EventBase
         :event/type (s/eq :congregation.event/permission-revoked)
         :event/version (s/eq 1)
         :congregation/id UUID
         :user/id UUID
         :permission/id PermissionId))

(s/defschema GisUserCreated
  (assoc EventBase
         :event/type (s/eq :congregation.event/gis-user-created)
         :event/version (s/eq 1)
         :congregation/id UUID
         :user/id UUID
         :gis-user/username s/Str
         :gis-user/password s/Str))
(s/defschema GisUserDeleted
  (assoc EventBase
         :event/type (s/eq :congregation.event/gis-user-deleted)
         :event/version (s/eq 1)
         :congregation/id UUID
         :user/id UUID
         :gis-user/username s/Str))

(def event-schemas
  {:congregation.event/congregation-created CongregationCreated
   :congregation.event/permission-granted PermissionGranted
   :congregation.event/permission-revoked PermissionRevoked
   :congregation.event/gis-user-created GisUserCreated
   :congregation.event/gis-user-deleted GisUserDeleted})

(s/defschema Event
  (s/constrained
   (apply refined/dispatch-on :event/type (flatten (seq event-schemas)))
   (fn [event]
     (not= (contains? event :event/user)
           (contains? event :event/system)))
   '(xor-required-key :event/user :event/system)))

;;; Validation

(defn validate-event [event]
  (when-not (contains? event-schemas (:event/type event))
    (throw (ex-info (str "Unknown event type " (pr-str (:event/type event)))
                    {:event event})))
  (assert (contains? event-schemas (:event/type event))
          {:error [:unknown-event-type (:event/type event)]
           :event event})
  (s/validate Event event))

(defn validate-events [events]
  (doseq [event events]
    (validate-event event))
  events)

;;; Serialization

(defn- string->instant [s]
  (if (string? s)
    (Instant/parse s)
    s))

(def ^:private datestring-coercion-matcher
  {Instant string->instant})

(defn- coercion-matcher [schema]
  (or (datestring-coercion-matcher schema)
      (coerce/string-coercion-matcher schema)))

(def ^:private coerce-event-commons
  (coerce/coercer (tools/open-schema EventBase) coercion-matcher))

(def ^:private coerce-event-specifics
  (coerce/coercer Event coercion-matcher))

(defn- coerce-event [event]
  ;; must coerce the common fields first, so that Event can
  ;; choose the right event schema based on the event type
  (let [result (coerce-event-commons event)]
    (if (schema.utils/error? result)
      result
      (coerce-event-specifics result))))

(defn json->event [json]
  (when json
    (let [result (coerce-event (json/parse-string json))]
      (when (schema.utils/error? result)
        (throw (ex-info "Event schema validation failed"
                        {:json json
                         :error result})))
      result)))

(defn event->json [event]
  (json/generate-string (validate-event event)))
