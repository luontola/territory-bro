;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.commands
  (:require [schema-refined.core :as refined]
            [schema.core :as s]
            [territory-bro.events :as events]
            [territory-bro.infra.foreign-key :as foreign-key]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.permissions :as permissions])
  (:import (java.time Instant)
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
   (s/optional-key :command/user) (foreign-key/references :user UUID)
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

(s/defschema RenameCongregation
  (assoc BaseCommand
         :command/type (s/eq :congregation.command/rename-congregation)
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation/name s/Str))

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

(s/defschema CreateTerritory
  (assoc GisSyncCommand
         :command/type (s/eq :territory.command/create-territory)
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :new UUID)
         :territory/number s/Str
         :territory/addresses s/Str
         :territory/region s/Str
         :territory/meta {s/Keyword json/Schema}
         :territory/location s/Str))

(s/defschema UpdateTerritory
  (assoc GisSyncCommand
         :command/type (s/eq :territory.command/update-territory)
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :territory UUID)
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

;;; Region

(s/defschema CreateRegion
  (assoc GisSyncCommand
         :command/type (s/eq :region.command/create-region)
         :congregation/id (foreign-key/references :congregation UUID)
         :region/id (foreign-key/references :new UUID)
         :region/name s/Str
         :region/location s/Str))

(s/defschema UpdateRegion
  (assoc GisSyncCommand
         :command/type (s/eq :region.command/update-region)
         :congregation/id (foreign-key/references :congregation UUID)
         :region/id (foreign-key/references :region UUID)
         :region/name s/Str
         :region/location s/Str))

(s/defschema DeleteRegion
  (assoc GisSyncCommand
         :command/type (s/eq :region.command/delete-region)
         :congregation/id (foreign-key/references :congregation UUID)
         :region/id (foreign-key/references :region UUID)))

;;; Congregation Boundary

(s/defschema CreateCongregationBoundary
  (assoc GisSyncCommand
         :command/type (s/eq :congregation-boundary.command/create-congregation-boundary)
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation-boundary/id (foreign-key/references :new UUID)
         :congregation-boundary/location s/Str))

(s/defschema UpdateCongregationBoundary
  (assoc GisSyncCommand
         :command/type (s/eq :congregation-boundary.command/update-congregation-boundary)
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation-boundary/id (foreign-key/references :congregation-boundary UUID)
         :congregation-boundary/location s/Str))

(s/defschema DeleteCongregationBoundary
  (assoc GisSyncCommand
         :command/type (s/eq :congregation-boundary.command/delete-congregation-boundary)
         :congregation/id (foreign-key/references :congregation UUID)
         :congregation-boundary/id (foreign-key/references :congregation-boundary UUID)))

;;; Card Minimap Viewport

(s/defschema CreateCardMinimapViewport
  (assoc GisSyncCommand
         :command/type (s/eq :card-minimap-viewport.command/create-card-minimap-viewport)
         :congregation/id (foreign-key/references :congregation UUID)
         :card-minimap-viewport/id (foreign-key/references :new UUID)
         :card-minimap-viewport/location s/Str))

(s/defschema UpdateCardMinimapViewport
  (assoc GisSyncCommand
         :command/type (s/eq :card-minimap-viewport.command/update-card-minimap-viewport)
         :congregation/id (foreign-key/references :congregation UUID)
         :card-minimap-viewport/id (foreign-key/references :card-minimap-viewport UUID)
         :card-minimap-viewport/location s/Str))

(s/defschema DeleteCardMinimapViewport
  (assoc GisSyncCommand
         :command/type (s/eq :card-minimap-viewport.command/delete-card-minimap-viewport)
         :congregation/id (foreign-key/references :congregation UUID)
         :card-minimap-viewport/id (foreign-key/references :card-minimap-viewport UUID)))

;;; Shares

(s/defschema ShareTerritoryLink
  (assoc BaseCommand
         :command/type (s/eq :share.command/share-territory-link)
         :share/id (foreign-key/references :new UUID)
         :share/key s/Str
         :congregation/id (foreign-key/references :congregation UUID)
         :territory/id (foreign-key/references :territory UUID)))

(s/defschema RecordShareOpened
  (assoc BaseCommand
         :command/type (s/eq :share.command/record-share-opened)
         :share/id (foreign-key/references :share UUID)))


(def command-schemas
  {:card-minimap-viewport.command/create-card-minimap-viewport CreateCardMinimapViewport
   :card-minimap-viewport.command/delete-card-minimap-viewport DeleteCardMinimapViewport
   :card-minimap-viewport.command/update-card-minimap-viewport UpdateCardMinimapViewport
   :congregation-boundary.command/create-congregation-boundary CreateCongregationBoundary
   :congregation-boundary.command/delete-congregation-boundary DeleteCongregationBoundary
   :congregation-boundary.command/update-congregation-boundary UpdateCongregationBoundary
   :congregation.command/add-user AddUser
   :congregation.command/create-congregation CreateCongregation
   :congregation.command/rename-congregation RenameCongregation
   :congregation.command/set-user-permissions SetUserPermissions
   :db-admin.command/ensure-gis-user-absent EnsureGisUserAbsent
   :db-admin.command/ensure-gis-user-present EnsureGisUserPresent
   :db-admin.command/migrate-tenant-schema MigrateTenantSchema
   :gis-user.command/create-gis-user CreateGisUser
   :gis-user.command/delete-gis-user DeleteGisUser
   :region.command/create-region CreateRegion
   :region.command/delete-region DeleteRegion
   :region.command/update-region UpdateRegion
   :share.command/record-share-opened RecordShareOpened
   :share.command/share-territory-link ShareTerritoryLink
   :territory.command/create-territory CreateTerritory
   :territory.command/delete-territory DeleteTerritory
   :territory.command/update-territory UpdateTerritory})

(s/defschema Command
  (apply refined/dispatch-on :command/type (flatten (seq command-schemas))))


;;;; Validation

(def ^:private command-validator (s/validator Command))

(defn validate-command [command]
  (when-not (contains? command-schemas (:command/type command))
    (throw (ex-info (str "Unknown command type " (pr-str (:command/type command)))
                    {:command command})))
  (assert (contains? command-schemas (:command/type command))
          {:error [:unknown-command-type (:command/type command)]
           :command command})
  (command-validator command))

(defn check-permit [state {user :command/user, system :command/system, :as command} permit]
  (cond
    (some? system) nil ; allow everything for system
    (some? user) (permissions/check state user permit)
    :else (throw (IllegalArgumentException.
                  (str ":command/user or :command/system required, but was: " (pr-str command))))))
