;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.commands
  (:require [schema-refined.core :as refined]
            [schema.core :as s]
            [territory-bro.events :as events]
            [territory-bro.infra.foreign-key :as foreign-key]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.permissions :as permissions])
  (:import (java.time Instant LocalDate)
           (java.util UUID)))

(def ^:private key-order
  (->> [:command/type
        :command/user
        :command/system
        :command/time]
       (map-indexed (fn [idx k]
                      [k idx]))
       (into {})))

(defn- key-comparator [x y]
  (compare [(get key-order x 100) x]
           [(get key-order y 100) y]))

(defn sorted-keys [command]
  (when command
    (into (sorted-map-by key-comparator)
          command)))


;;;; Schemas

(s/defschema BaseCommand
  {:command/type s/Keyword
   :command/time Instant
   (s/optional-key :command/user) (foreign-key/references :user-or-anonymous UUID)
   (s/optional-key :command/system) s/Str})

(s/defschema GisSyncCommand
  (assoc BaseCommand
         (s/optional-key :gis-change/id) s/Int))

;;; Congregation

(s/defschema CreateCongregation
  (assoc BaseCommand
         :command/type (s/eq :congregation.command/create-congregation)
         :congregation/id (foreign-key/references :new UUID)
         :congregation/name s/Str))

(s/defschema AddUser
  (assoc BaseCommand
         :command/type (s/eq :congregation.command/add-user)
         :congregation/id (foreign-key/references :congregation UUID)
         :user/id (foreign-key/references :user UUID)))

(s/defschema SetUserPermissions
  (assoc BaseCommand
         :command/type (s/eq :congregation.command/set-user-permissions)
         :congregation/id (foreign-key/references :congregation UUID)
         :user/id (foreign-key/references :user UUID)
         :permission/ids [events/PermissionId]))

(s/defschema UpdateCongregation
  (assoc BaseCommand
         :command/type (s/eq :congregation.command/update-congregation)
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation/name s/Str
         (s/optional-key :congregation/loans-csv-url) s/Str))

;;; DB Admin

(s/defschema EnsureGisUserAbsent
  (assoc BaseCommand
         :command/type (s/eq :db-admin.command/ensure-gis-user-absent)
         :user/id (foreign-key/references :user UUID)
         :gis-user/username s/Str
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation/schema-name s/Str))

(s/defschema EnsureGisUserPresent
  (assoc BaseCommand
         :command/type (s/eq :db-admin.command/ensure-gis-user-present)
         :user/id (foreign-key/references :user UUID)
         :gis-user/username s/Str
         :gis-user/password s/Str
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation/schema-name s/Str))

(s/defschema MigrateTenantSchema
  (assoc BaseCommand
         :command/type (s/eq :db-admin.command/migrate-tenant-schema)
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation/schema-name s/Str))

;;; GIS User

(s/defschema CreateGisUser
  (assoc BaseCommand
         :command/type (s/eq :gis-user.command/create-gis-user)
         :congregation/id (foreign-key/references :congregation UUID)
         :user/id (foreign-key/references :user UUID)))

(s/defschema DeleteGisUser
  (assoc BaseCommand
         :command/type (s/eq :gis-user.command/delete-gis-user)
         :congregation/id (foreign-key/references :congregation UUID)
         :user/id (foreign-key/references :user UUID)))

;;; Territory

(s/defschema DefineTerritory
  (assoc GisSyncCommand
         :command/type (s/eq :territory.command/define-territory)
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :unsafe UUID)
         :territory/number s/Str
         :territory/addresses s/Str
         :territory/region s/Str
         :territory/meta {s/Keyword json/Schema}
         :territory/location s/Str))

(s/defschema DeleteTerritory
  (assoc GisSyncCommand
         :command/type (s/eq :territory.command/delete-territory)
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :territory UUID)))

(s/defschema AssignTerritory
  (assoc BaseCommand
         :command/type (s/eq :territory.command/assign-territory)
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :territory UUID)
         :assignment/id (foreign-key/references :unsafe UUID) ; assignments don't have their own stream
         :date LocalDate
         :publisher/id (foreign-key/references :publisher UUID)))

(s/defschema ReturnTerritory
  (assoc BaseCommand
         :command/type (s/eq :territory.command/return-territory)
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :territory UUID)
         :assignment/id (foreign-key/references :unsafe UUID) ; assignments don't have their own stream
         :date LocalDate
         :returning? boolean
         :covered? boolean))

;;; Region

(s/defschema DefineRegion
  (assoc GisSyncCommand
         :command/type (s/eq :region.command/define-region)
         :congregation/id (foreign-key/references :congregation UUID)
         :region/id (foreign-key/references :unsafe UUID)
         :region/name s/Str
         :region/location s/Str))

(s/defschema DeleteRegion
  (assoc GisSyncCommand
         :command/type (s/eq :region.command/delete-region)
         :congregation/id (foreign-key/references :congregation UUID)
         :region/id (foreign-key/references :region UUID)))

;;; Congregation Boundary

(s/defschema DefineCongregationBoundary
  (assoc GisSyncCommand
         :command/type (s/eq :congregation-boundary.command/define-congregation-boundary)
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation-boundary/id (foreign-key/references :unsafe UUID)
         :congregation-boundary/location s/Str))

(s/defschema DeleteCongregationBoundary
  (assoc GisSyncCommand
         :command/type (s/eq :congregation-boundary.command/delete-congregation-boundary)
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation-boundary/id (foreign-key/references :congregation-boundary UUID)))

;;; Card Minimap Viewport

(s/defschema DefineCardMinimapViewport
  (assoc GisSyncCommand
         :command/type (s/eq :card-minimap-viewport.command/define-card-minimap-viewport)
         :congregation/id (foreign-key/references :congregation UUID)
         :card-minimap-viewport/id (foreign-key/references :unsafe UUID)
         :card-minimap-viewport/location s/Str))

(s/defschema DeleteCardMinimapViewport
  (assoc GisSyncCommand
         :command/type (s/eq :card-minimap-viewport.command/delete-card-minimap-viewport)
         :congregation/id (foreign-key/references :congregation UUID)
         :card-minimap-viewport/id (foreign-key/references :card-minimap-viewport UUID)))

;;; Shares

(s/defschema CreateShare
  (assoc BaseCommand
         :command/type (s/eq :share.command/create-share)
         :share/id (foreign-key/references :new UUID)
         :share/key s/Str
         :share/type (s/enum :link :qr-code)
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :territory UUID)))

(s/defschema RecordShareOpened
  (assoc BaseCommand
         :command/type (s/eq :share.command/record-share-opened)
         :share/id (foreign-key/references :share UUID)))

;;; Do-Not-Calls

(s/defschema SaveDoNotCalls
  (assoc BaseCommand
         :command/type (s/eq :do-not-calls.command/save-do-not-calls)
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :territory UUID)
         :territory/do-not-calls s/Str))


(def command-schemas
  {:card-minimap-viewport.command/define-card-minimap-viewport DefineCardMinimapViewport
   :card-minimap-viewport.command/delete-card-minimap-viewport DeleteCardMinimapViewport
   :congregation-boundary.command/define-congregation-boundary DefineCongregationBoundary
   :congregation-boundary.command/delete-congregation-boundary DeleteCongregationBoundary
   :congregation.command/add-user AddUser
   :congregation.command/create-congregation CreateCongregation
   :congregation.command/set-user-permissions SetUserPermissions
   :congregation.command/update-congregation UpdateCongregation
   :db-admin.command/ensure-gis-user-absent EnsureGisUserAbsent
   :db-admin.command/ensure-gis-user-present EnsureGisUserPresent
   :db-admin.command/migrate-tenant-schema MigrateTenantSchema
   :do-not-calls.command/save-do-not-calls SaveDoNotCalls
   :gis-user.command/create-gis-user CreateGisUser
   :gis-user.command/delete-gis-user DeleteGisUser
   :region.command/define-region DefineRegion
   :region.command/delete-region DeleteRegion
   :share.command/create-share CreateShare
   :share.command/record-share-opened RecordShareOpened
   :territory.command/assign-territory AssignTerritory
   :territory.command/define-territory DefineTerritory
   :territory.command/delete-territory DeleteTerritory
   :territory.command/return-territory ReturnTerritory})

(s/defschema Command
  (apply refined/dispatch-on :command/type (flatten (seq command-schemas))))


;;;; Validation

(def ^:private command-validator (s/validator Command))

(defn validate-command [command]
  (when-not (contains? command-schemas (:command/type command))
    (throw (ex-info (str "Unknown command type " (pr-str (:command/type command)))
                    {:command command})))
  (command-validator command))

(defn check-permit [state {user :command/user, system :command/system, :as command} permit]
  (cond
    (some? system) nil ; allow everything for system
    (some? user) (permissions/check state user permit)
    :else (throw (IllegalArgumentException.
                  (str ":command/user or :command/system required, but was: " (pr-str command))))))
