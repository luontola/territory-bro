;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.commands
  (:require [schema-refined.core :as refined]
            [schema.core :as s]
            [schema.utils]
            [territory-bro.events :as events]
            [territory-bro.foreign-key :as foreign-key]
            [territory-bro.permissions :as permissions])
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

(defn sorted-keys [event]
  (when event
    (into (sorted-map-by key-comparator)
          event)))


;;;; Schemas

(s/defschema BaseCommand
  {:command/type s/Keyword
   :command/time Instant
   (s/optional-key :command/user) (foreign-key/references :user UUID)
   (s/optional-key :command/system) s/Str})


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


(def command-schemas
  {:congregation.command/add-user AddUser
   :congregation.command/create-congregation CreateCongregation
   :congregation.command/rename-congregation RenameCongregation
   :congregation.command/set-user-permissions SetUserPermissions
   :db-admin.command/ensure-gis-user-absent EnsureGisUserAbsent
   :db-admin.command/ensure-gis-user-present EnsureGisUserPresent
   :db-admin.command/migrate-tenant-schema MigrateTenantSchema
   :gis-user.command/create-gis-user CreateGisUser
   :gis-user.command/delete-gis-user DeleteGisUser})

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
    (= (nil? user)
       (nil? system))
    (throw (IllegalArgumentException.
            (str "Either :command/user or :command/system required, but was: " (pr-str command))))
    (some? user) (permissions/check state user permit)
    (some? system) nil)) ; allow everything for system
