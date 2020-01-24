;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.events
  (:require [medley.core :refer [map-keys]]
            [schema-refined.core :as refined]
            [schema-tools.core :as tools]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [schema.utils]
            [territory-bro.json :as json])
  (:import (java.time Instant)
           (java.util UUID)))

(defn- enrich-event [event command current-time]
  (let [{:command/keys [user system]} command]
    (-> event
        (assoc :event/time current-time)
        (cond->
          user (assoc :event/user user)
          system (assoc :event/system system)))))

(defn enrich-events [command {:keys [now]} events]
  (assert (some? now))
  (let [current-time (now)]
    (map #(enrich-event % command current-time) events)))


(def ^:private key-order
  (->> [:event/type
        :event/version
        :event/transient?
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
  (when event
    (into (sorted-map-by key-comparator)
          event)))


;;;; Schemas

(s/defschema EventBase
  {(s/optional-key :event/stream-id) UUID
   (s/optional-key :event/stream-revision) s/Int
   (s/optional-key :event/global-revision) s/Int
   :event/type s/Keyword
   :event/version s/Int
   (s/optional-key :event/time) Instant
   (s/optional-key :event/user) UUID
   (s/optional-key :event/system) s/Str})

;;; Congregation

(s/defschema CongregationCreated
  (assoc EventBase
         :event/type (s/eq :congregation.event/congregation-created)
         :event/version (s/eq 1)
         :congregation/id UUID
         :congregation/name s/Str
         :congregation/schema-name s/Str))
(s/defschema CongregationRenamed
  (assoc EventBase
         :event/type (s/eq :congregation.event/congregation-renamed)
         :event/version (s/eq 1)
         :congregation/id UUID
         :congregation/name s/Str))

(s/defschema PermissionId
  (s/enum :view-congregation
          :configure-congregation
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

;;; Subregion

(s/defschema SubregionDefined
  (assoc EventBase
         :event/type (s/eq :subregion.event/subregion-defined)
         :event/version (s/eq 1)
         :congregation/id UUID
         :subregion/id UUID
         :subregion/name s/Str
         :subregion/location s/Str))
(s/defschema SubregionDeleted
  (assoc EventBase
         :event/type (s/eq :subregion.event/subregion-deleted)
         :event/version (s/eq 1)
         :congregation/id UUID
         :subregion/id UUID))

;;; DB Admin

(s/defschema GisSchemaIsPresent
  (assoc EventBase
         :event/type (s/eq :db-admin.event/gis-schema-is-present)
         :event/version (s/eq 1)
         :event/transient? (s/eq true)
         :congregation/id UUID
         :congregation/schema-name s/Str))
(s/defschema GisUserIsPresent
  (assoc EventBase
         :event/type (s/eq :db-admin.event/gis-user-is-present)
         :event/version (s/eq 1)
         :event/transient? (s/eq true)
         :congregation/id UUID
         :user/id UUID
         :gis-user/username s/Str))
(s/defschema GisUserIsAbsent
  (assoc EventBase
         :event/type (s/eq :db-admin.event/gis-user-is-absent)
         :event/version (s/eq 1)
         :event/transient? (s/eq true)
         :congregation/id UUID
         :user/id UUID
         :gis-user/username s/Str))

(def event-schemas
  {:congregation.event/congregation-created CongregationCreated
   :congregation.event/congregation-renamed CongregationRenamed
   :congregation.event/permission-granted PermissionGranted
   :congregation.event/permission-revoked PermissionRevoked
   :congregation.event/gis-user-created GisUserCreated
   :congregation.event/gis-user-deleted GisUserDeleted
   :subregion.event/subregion-defined SubregionDefined
   :subregion.event/subregion-deleted SubregionDeleted
   :db-admin.event/gis-schema-is-present GisSchemaIsPresent
   :db-admin.event/gis-user-is-present GisUserIsPresent
   :db-admin.event/gis-user-is-absent GisUserIsAbsent})

(s/defschema Event
  (apply refined/dispatch-on :event/type (flatten (seq event-schemas))))

(defn- required-key [m k]
  (map-keys #(if (= % (s/optional-key k))
               k
               %)
            m))

(s/defschema EnrichedEvent
  (s/constrained
   (-> EventBase
       (required-key :event/time)
       (assoc s/Any s/Any))
   (fn [event]
     (not= (contains? event :event/user)
           (contains? event :event/system)))
   '(xor-required-key :event/user :event/system)))


;;;; Validation

(def ^:private event-validator (s/validator Event))

(defn validate-event [event]
  (when-not (contains? event-schemas (:event/type event))
    (throw (ex-info (str "Unknown event type " (pr-str (:event/type event)))
                    {:event event})))
  (event-validator event))

(defn validate-events [events]
  (doseq [event events]
    (validate-event event))
  events)

(def ^:private enriched-event-validator (s/validator EnrichedEvent))

(defn strict-validate-event [event]
  (validate-event event)
  (enriched-event-validator event))

(defn strict-validate-events [events]
  (doseq [event events]
    (strict-validate-event event))
  events)


;;;; Serialization

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
  (coerce/coercer! (tools/open-schema EventBase) coercion-matcher))

(def ^:private coerce-event-specifics
  (coerce/coercer! Event coercion-matcher))

(defn- coerce-event [event]
  ;; must coerce the common fields first, so that Event can
  ;; choose the right event schema based on the event type
  (coerce-event-specifics (coerce-event-commons event)))

(defn json->event [json]
  (when json
    (coerce-event (json/parse-string json))))

(defn event->json [event]
  (json/generate-string (strict-validate-event event)))
