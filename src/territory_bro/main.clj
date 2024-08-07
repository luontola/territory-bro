;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.main
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [ring.adapter.jetty :as httpd]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.gis.gis-sync :as gis-sync]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.router :as router]
            [territory-bro.migration :as migration]
            [territory-bro.projections :as projections])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
           (org.eclipse.jetty.server Server)
           (org.eclipse.jetty.server.handler.gzip GzipHandler))
  (:gen-class))

(mount/defstate ^{:tag Server, :on-reload :noop} http-server
  :start
  (httpd/run-jetty #'router/app
                   {:port (:port config/env)
                    :join? false
                    :configurator (fn [^Server server]
                                    (.insertHandler server (GzipHandler.)))})
  :stop
  (.stop ^Server http-server))

(defn- migrate-application-state! []
  (let [injections {:now (:now config/env)}
        state (projections/cached-state)]
    (db/with-db [conn {}]
      (doseq [command (migration/generate-commands state injections)]
        (dispatcher/command! conn state command)))))

(defn migrate-database! []
  (db/check-database-version db/expected-postgresql-version)
  (db/migrate-master-schema!)
  ;; process managers will migrate tenant schemas and create missing GIS users
  (projections/refresh!)
  (migrate-application-state!)
  ;; process any pending GIS changes, in case GIS sync was down for a long time
  (gis-sync/refresh!))

(defn- log-mount-states [result]
  (doseq [component (:started result)]
    (log/info component "started"))
  (doseq [component (:stopped result)]
    (log/info component "stopped")))

(defn stop-app []
  (log-mount-states (mount/stop)))

(defn start-app []
  ;; start the public API only after the database is ready
  (log-mount-states (mount/start #'config/env
                                 #'db/datasource
                                 #'projections/*cache
                                 #'projections/refresher))
  (migrate-database!)
  (log/info "Database migrated")
  (log-mount-states (mount/start))

  (doto (Runtime/getRuntime)
    (.addShutdownHook (Thread. ^Runnable stop-app)))
  (log/info "Started"))

(defn -main [& args]
  (try
    (start-app)
    (catch Throwable t
      ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
      (log/error t (str "Failed to start\n" (pr-str t)))
      (System/exit 1)))

  ;; Helper for Application Class-Data Sharing (AppCDS)
  ;; See https://docs.oracle.com/en/java/javase/11/vm/class-data-sharing.html
  (when (= "app-cds-setup" (first args))
    ;; do one API request to force more classes to be loaded
    (let [client (-> (HttpClient/newBuilder)
                     (.build))
          request (-> (HttpRequest/newBuilder)
                      (.uri (URI. (str "http://localhost:" (:port config/env))))
                      (.build))]
      (.send client request (HttpResponse$BodyHandlers/ofString)))
    (stop-app)
    (System/exit 0)))
