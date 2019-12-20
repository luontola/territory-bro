;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dispatcher
  (:require [territory-bro.commands :as commands]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.event-store :as event-store]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.user :as user]))

(defn- congregation-command! [conn command state]
  (let [injections {:now (:now config/env)
                    :check-permit (fn [permit]
                                    (commands/check-permit state command permit))
                    :user-exists? (fn [user-id]
                                    (db/with-db [conn {:read-only? true}]
                                      (some? (user/get-by-id conn user-id))))}
        stream-id (:congregation/id command)
        old-events (event-store/read-stream conn stream-id)
        new-events (congregation/handle-command command old-events injections)]
    (event-store/save! conn stream-id (count old-events) new-events)))

(defn- gis-user-command! [conn command state]
  (let [injections {:now (:now config/env)
                    :check-permit (fn [permit]
                                    (commands/check-permit state command permit))
                    :generate-password #(gis-user/generate-password 50)
                    :db-user-exists? #(gis-user/db-user-exists? conn %)}
        ;; TODO: the GIS user events would belong better to a user-specific stream
        stream-id (:congregation/id command)
        old-events (event-store/read-stream conn stream-id)
        new-events (gis-user/handle-command command old-events injections)]
    (event-store/save! conn stream-id (count old-events) new-events)))

(defn- db-admin-command! [conn command state]
  (let [injections {:now (:now config/env)
                    :check-permit (fn [permit]
                                    (commands/check-permit state command permit))
                    :migrate-tenant-schema! db/migrate-tenant-schema!
                    :ensure-gis-user-present! (fn [args]
                                                (gis-user/ensure-present! conn args))
                    :ensure-gis-user-absent! (fn [args]
                                               (gis-user/ensure-absent! conn args))}]
    (db-admin/handle-command command state injections)))

(defn command! [conn state command]
  (let [command (commands/validate-command command)]
    (case (namespace (:command/type command))
      "congregation.command" (congregation-command! conn command state)
      "gis-user.command" (gis-user-command! conn command state)
      "db-admin.command" (db-admin-command! conn command state)
      (throw (IllegalArgumentException. (str "Unrecognized command: " (pr-str command)))))))
