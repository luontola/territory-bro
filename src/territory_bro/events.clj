;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.events
  (:require [schema-refined.core :as r]
            [schema.core :as s])
  (:import (java.time Instant)
           (java.util UUID)))

(s/defschema EventBase
  {(s/optional-key :event/stream-id) UUID
   (s/optional-key :event/stream-revision) s/Int
   (s/optional-key :event/global-revision) s/Int
   :event/type s/Keyword
   :event/version s/Int
   (s/optional-key :event/time) Instant ;; TODO: add JSON support for Instant and make this field mandatory
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
  (apply r/dispatch-on :event/type (flatten (seq event-schemas))))

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
