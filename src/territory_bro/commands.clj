;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.commands
  (:require [schema-refined.core :as refined]
            [schema.core :as s]
            [schema.utils]
            [territory-bro.events :as events]
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

(s/defschema UserCommand
  {:command/type s/Keyword
   :command/time Instant
   :command/user UUID})

(s/defschema SystemCommand
  {:command/type s/Keyword
   :command/time Instant
   :command/system s/Str})


;;; Congregation

(s/defschema CreateCongregation
  (assoc UserCommand
         :command/type (s/eq :congregation.command/create-congregation)
         :congregation/id UUID
         :congregation/name s/Str))

(s/defschema AddUser
  (assoc UserCommand
         :command/type (s/eq :congregation.command/add-user)
         :congregation/id UUID
         :user/id UUID))

(s/defschema SetUserPermissions
  (assoc UserCommand
         :command/type (s/eq :congregation.command/set-user-permissions)
         :congregation/id UUID
         :user/id UUID
         :permission/ids [events/PermissionId]))

(s/defschema RenameCongregation
  (assoc UserCommand
         :command/type (s/eq :congregation.command/rename-congregation)
         :congregation/id UUID
         :congregation/name s/Str))


;;; DB Admin

(s/defschema EnsureGisUserAbsent
  (assoc SystemCommand
         :command/type (s/eq :db-admin.command/ensure-gis-user-absent)
         :user/id UUID
         :gis-user/username s/Str
         :congregation/id UUID
         :congregation/schema-name s/Str))

(s/defschema EnsureGisUserPresent
  (assoc SystemCommand
         :command/type (s/eq :db-admin.command/ensure-gis-user-present)
         :user/id UUID
         :gis-user/username s/Str
         :gis-user/password s/Str
         :congregation/id UUID
         :congregation/schema-name s/Str))

(s/defschema MigrateTenantSchema
  (assoc SystemCommand
         :command/type (s/eq :db-admin.command/migrate-tenant-schema)
         :congregation/id UUID
         :congregation/schema-name s/Str))


;;; GIS User

(s/defschema CreateGisUser
  (assoc SystemCommand
         :command/type (s/eq :gis-user.command/create-gis-user)
         :congregation/id UUID
         :user/id UUID))

(s/defschema DeleteGisUser
  (assoc SystemCommand
         :command/type (s/eq :gis-user.command/delete-gis-user)
         :congregation/id UUID
         :user/id UUID))


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

(defn validate-command [command]
  (when-not (contains? command-schemas (:command/type command))
    (throw (ex-info (str "Unknown command type " (pr-str (:command/type command)))
                    {:command command})))
  (assert (contains? command-schemas (:command/type command))
          {:error [:unknown-command-type (:command/type command)]
           :command command})
  (s/validate Command command))

(defn validate-commands [commands]
  (doseq [command commands]
    (validate-command command))
  commands)

(defn check-permit [state {user :command/user, system :command/system, :as command} permit]
  (cond
    (= (nil? user)
       (nil? system))
    (throw (IllegalArgumentException.
            (str "Either :command/user or :command/system required, but was: " (pr-str command))))
    (some? user) (permissions/check state user permit)
    (some? system) nil)) ; allow everything for system
