;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.events
  (:require [medley.core :refer [map-keys]]
            [schema-refined.core :as refined]
            [schema-tools.core :as tools]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [territory-bro.infra.json :as json])
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

(s/defschema BaseEvent
  {(s/optional-key :event/stream-id) UUID
   (s/optional-key :event/stream-revision) s/Int
   (s/optional-key :event/global-revision) s/Int
   :event/type s/Keyword
   (s/optional-key :event/time) Instant
   (s/optional-key :event/user) UUID
   (s/optional-key :event/system) s/Str})

(s/defschema GisSyncEvent
  (assoc BaseEvent
         (s/optional-key :gis-change/id) s/Int))

;;; Congregation

(s/defschema CongregationCreated
  (assoc BaseEvent
         :event/type (s/eq :congregation.event/congregation-created)
         :congregation/id UUID
         :congregation/name s/Str
         :congregation/schema-name s/Str))
(s/defschema CongregationRenamed
  (assoc BaseEvent
         :event/type (s/eq :congregation.event/congregation-renamed)
         :congregation/id UUID
         :congregation/name s/Str))

;; TODO: remove duplication of the default grants list
(s/defschema PermissionId
  (s/enum :view-congregation
          :configure-congregation
          :gis-access
          :share-territory-link))
(s/defschema PermissionGranted
  (assoc BaseEvent
         :event/type (s/eq :congregation.event/permission-granted)
         :congregation/id UUID
         :user/id UUID
         :permission/id PermissionId))
(s/defschema PermissionRevoked
  (assoc BaseEvent
         :event/type (s/eq :congregation.event/permission-revoked)
         :congregation/id UUID
         :user/id UUID
         :permission/id PermissionId))

(s/defschema GisUserCreated
  (assoc BaseEvent
         :event/type (s/eq :congregation.event/gis-user-created)
         :congregation/id UUID
         :user/id UUID
         :gis-user/username s/Str
         :gis-user/password s/Str))
(s/defschema GisUserDeleted
  (assoc BaseEvent
         :event/type (s/eq :congregation.event/gis-user-deleted)
         :congregation/id UUID
         :user/id UUID
         :gis-user/username s/Str))

;;; Territory

(s/defschema TerritoryDefined
  (assoc GisSyncEvent
         :event/type (s/eq :territory.event/territory-defined)
         :congregation/id UUID
         :territory/id UUID
         :territory/number s/Str
         :territory/addresses s/Str
         :territory/region s/Str
         :territory/meta {s/Keyword json/Schema}
         :territory/location s/Str))
(s/defschema TerritoryDeleted
  (assoc GisSyncEvent
         :event/type (s/eq :territory.event/territory-deleted)
         :congregation/id UUID
         :territory/id UUID))

;;; Region

(s/defschema RegionDefined
  (assoc GisSyncEvent
         :event/type (s/eq :region.event/region-defined)
         :congregation/id UUID
         :region/id UUID
         :region/name s/Str
         :region/location s/Str))
(s/defschema RegionDeleted
  (assoc GisSyncEvent
         :event/type (s/eq :region.event/region-deleted)
         :congregation/id UUID
         :region/id UUID))

;;; Congregation Boundary

(s/defschema CongregationBoundaryDefined
  (assoc GisSyncEvent
         :event/type (s/eq :congregation-boundary.event/congregation-boundary-defined)
         :congregation/id UUID
         :congregation-boundary/id UUID
         :congregation-boundary/location s/Str))
(s/defschema CongregationBoundaryDeleted
  (assoc GisSyncEvent
         :event/type (s/eq :congregation-boundary.event/congregation-boundary-deleted)
         :congregation/id UUID
         :congregation-boundary/id UUID))

;;; Card Minimap Viewport

(s/defschema CardMinimapViewportDefined
  (assoc GisSyncEvent
         :event/type (s/eq :card-minimap-viewport.event/card-minimap-viewport-defined)
         :congregation/id UUID
         :card-minimap-viewport/id UUID
         :card-minimap-viewport/location s/Str))
(s/defschema CardMinimapViewportDeleted
  (assoc GisSyncEvent
         :event/type (s/eq :card-minimap-viewport.event/card-minimap-viewport-deleted)
         :congregation/id UUID
         :card-minimap-viewport/id UUID))

;;; DB Admin

(s/defschema GisSchemaIsPresent
  (assoc BaseEvent
         :event/type (s/eq :db-admin.event/gis-schema-is-present)
         :event/transient? (s/eq true)
         :congregation/id UUID
         :congregation/schema-name s/Str))
(s/defschema GisUserIsPresent
  (assoc BaseEvent
         :event/type (s/eq :db-admin.event/gis-user-is-present)
         :event/transient? (s/eq true)
         :congregation/id UUID
         :user/id UUID
         :gis-user/username s/Str))
(s/defschema GisUserIsAbsent
  (assoc BaseEvent
         :event/type (s/eq :db-admin.event/gis-user-is-absent)
         :event/transient? (s/eq true)
         :congregation/id UUID
         :user/id UUID
         :gis-user/username s/Str))

;;; Shares

(s/defschema ShareCreated
  (assoc BaseEvent
         :event/type (s/eq :share.event/share-created)
         :share/id UUID
         :share/key s/Str
         :share/type (s/enum :link :qr-code)
         :congregation/id UUID
         :territory/id UUID))


(def event-schemas
  {:card-minimap-viewport.event/card-minimap-viewport-defined CardMinimapViewportDefined
   :card-minimap-viewport.event/card-minimap-viewport-deleted CardMinimapViewportDeleted
   :congregation-boundary.event/congregation-boundary-defined CongregationBoundaryDefined
   :congregation-boundary.event/congregation-boundary-deleted CongregationBoundaryDeleted
   :congregation.event/congregation-created CongregationCreated
   :congregation.event/congregation-renamed CongregationRenamed
   :congregation.event/gis-user-created GisUserCreated
   :congregation.event/gis-user-deleted GisUserDeleted
   :congregation.event/permission-granted PermissionGranted
   :congregation.event/permission-revoked PermissionRevoked
   :db-admin.event/gis-schema-is-present GisSchemaIsPresent
   :db-admin.event/gis-user-is-absent GisUserIsAbsent
   :db-admin.event/gis-user-is-present GisUserIsPresent
   :region.event/region-defined RegionDefined
   :region.event/region-deleted RegionDeleted
   :share.event/share-created ShareCreated
   :territory.event/territory-defined TerritoryDefined
   :territory.event/territory-deleted TerritoryDeleted})

(s/defschema Event
  (apply refined/dispatch-on :event/type (flatten (seq event-schemas))))

(defn- required-key [m k]
  (map-keys #(if (= % (s/optional-key k))
               k
               %)
            m))

(s/defschema EnrichedEvent
  (s/constrained
   (-> BaseEvent
       (required-key :event/time)
       (assoc s/Any s/Any))
   (fn [event]
     (or (contains? event :event/user)
         (contains? event :event/system)))
   '(any-of-required-keys :event/user :event/system)))


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
  (coerce/coercer! (tools/open-schema BaseEvent) coercion-matcher))

(def ^:private coerce-event-specifics
  (coerce/coercer! Event coercion-matcher))

(defn- coerce-event [event]
  ;; must coerce the common fields first, so that Event can
  ;; choose the right event schema based on the event type
  (coerce-event-specifics (coerce-event-commons event)))

(defn json->event [json]
  (when json
    (coerce-event (json/read-value json))))

(defn event->json [event]
  (json/write-value-as-string (strict-validate-event event)))
