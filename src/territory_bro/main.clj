;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.main
  (:require [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [luminus-migrations.core :as migrations]
            [luminus.http-server :as http]
            [luminus.repl-server :as repl]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.router :as router]
            [territory-bro.congregation :as congregation])
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
  (let [schema (:database-schema config/env)]
    (log/info "Migrating master schema" schema)
    (-> (db/master-schema schema)
        (.migrate)))

  (doseq [congregation (db/with-db [conn {}]
                         (congregation/get-unrestricted-congregations conn))]
    (let [schema (::congregation/schema-name congregation)]
      (log/info "Migrating tenant schema" schema)
      (-> (db/tenant-schema schema)
          (.migrate)))))

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
    (log-mount-states (mount/start-without #'http-server))
    (migrate-database!)
    ;; start the public API only after the database is ready
    (log-mount-states (mount/start #'http-server))
    (doto (Runtime/getRuntime)
          (.addShutdownHook (Thread. ^Runnable stop-app)))
    (log/info "Started")
    (catch Throwable t
      (log/error t "Failed to start")
      (stop-app))))

(defn -main [& args]
  (log-mount-states (mount/start #'config/env))
  (cond
    (nil? (:database-url config/env))
    (do
      (log/error "Database configuration not found, :database-url must be set before running")
      (System/exit 1))

    (some #{"init"} args)
    (do
      (migrations/init (select-keys config/env [:database-url :init-script]))
      (System/exit 0))

    (migrations/migration? args)
    (do
      (log-mount-states (mount/start #'db/databases))
      (migrate-database!)
      (migrations/migrate args (select-keys config/env [:database-url]))
      (System/exit 0))

    :else
    (start-app)))
