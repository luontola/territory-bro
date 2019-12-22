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
            [territory-bro.events :as events]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.user :as user]))

(defn- default-injections [command state]
  {:now (:now config/env)
   :check-permit (fn [permit]
                   (commands/check-permit state command permit))})

(defn- write-stream! [conn stream-id f]
  (let [old-events (event-store/read-stream conn stream-id)
        new-events (f old-events)]
    (event-store/save! conn stream-id (count old-events) new-events)))

(defn- call! [command-handler command state-or-old-events injections]
  (->> (command-handler command state-or-old-events injections)
       (events/enrich-events command injections)
       (events/strict-validate-events)))


(defn- congregation-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :user-exists? (fn [user-id]
                                          (db/with-db [conn {:read-only? true}]
                                            (some? (user/get-by-id conn user-id)))))]
    (write-stream! conn
                   (:congregation/id command)
                   (fn [old-events]
                     (call! congregation/handle-command command old-events injections)))))

(defn- gis-user-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :generate-password #(gis-user/generate-password 50)
                          :db-user-exists? #(gis-db/user-exists? conn %))]
    (write-stream! conn
                   (:congregation/id command) ; TODO: the GIS user events would belong better to a user-specific stream
                   (fn [old-events]
                     (call! gis-user/handle-command command old-events injections)))))

(defn- db-admin-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :migrate-tenant-schema! db/migrate-tenant-schema!
                          :ensure-gis-user-present! (fn [args]
                                                      (gis-db/ensure-user-present! conn args))
                          :ensure-gis-user-absent! (fn [args]
                                                     (gis-db/ensure-user-absent! conn args)))]
    (call! db-admin/handle-command command state injections)))

(defn command! [conn state command]
  (let [command (commands/validate-command command)]
    (case (namespace (:command/type command))
      "congregation.command" (congregation-command! conn command state)
      "gis-user.command" (gis-user-command! conn command state)
      "db-admin.command" (db-admin-command! conn command state)
      (throw (AssertionError. (str "Command handler not found: " (pr-str command)))))))
