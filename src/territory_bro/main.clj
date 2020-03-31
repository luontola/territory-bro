;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.main
  (:require [clojure.tools.logging :as log]
            [luminus.http-server :as http]
            [luminus.repl-server :as repl]
            [mount.core :as mount]
            [territory-bro.gis.gis-sync :as gis-sync]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.router :as router]
            [territory-bro.projections :as projections])
  (:gen-class))

(mount/defstate ^{:on-reload :noop} http-server
  :start
  (http/start {:handler #'router/app
               :port (:port config/env)
               :io-threads (* 2 (.availableProcessors (Runtime/getRuntime)))})
  :stop
  (http/stop http-server))

(mount/defstate ^{:on-reload :noop} repl-server
  :start
  (when (:nrepl-port config/env)
    (repl/start {:bind (:nrepl-bind config/env)
                 :port (:nrepl-port config/env)}))
  :stop
  (when repl-server
    (repl/stop repl-server)))

(defn migrate-database! []
  (db/check-database-version 11)
  (db/migrate-master-schema!)
  ;; process managers will migrate tenant schemas and create missing GIS users
  (projections/refresh!)
  ;; process any pending GIS changes, in case GIS sync was down for a long time
  (gis-sync/refresh!))

(defn- log-mount-states [result]
  (doseq [component (:started result)]
    (log/info component "started"))
  (doseq [component (:stopped result)]
    (log/info component "stopped")))

(defn stop-app []
  (log-mount-states (mount/stop))
  (shutdown-agents))

(defn start-app []
  (try
    ;; start the public API only after the database is ready
    (log-mount-states (mount/start #'config/env
                                   #'db/database
                                   #'projections/*cache
                                   #'projections/refresher))
    (migrate-database!)
    (log/info "Database migrated")
    (log-mount-states (mount/start))

    (doto (Runtime/getRuntime)
      (.addShutdownHook (Thread. ^Runnable stop-app)))
    (log/info "Started")
    (catch Throwable t
      ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
      (log/error t (str "Failed to start\n" (pr-str t)))
      (stop-app))))

(defn -main [& _args]
  (log-mount-states (mount/start #'config/env))
  (when (nil? (:database-url config/env))
    (log/error "Database configuration not found, :database-url must be set before running")
    (System/exit 1))
  (start-app))
