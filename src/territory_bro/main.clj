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

(def ^:private cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate ^{:on-reload :noop} http-server
  :start
  (http/start
    (-> config/env
        (assoc :handler #'router/app)
        (update :io-threads #(or % (* 2 (.availableProcessors (Runtime/getRuntime)))))
        (update :port #(or (-> config/env :options :port) %))))
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

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn start-app [args]
  (doseq [component (:started (mount/start-with-args (cli/parse-opts args cli-options)))]
    (log/info component "started"))

  (log/info "Migrating master schema" (:database-schema config/env))
  (-> (db/master-schema (:database-schema config/env))
      (.migrate))

  (doseq [congregation (db/with-db [conn {}]
                         (congregation/get-congregations conn))]
    (log/info "Migrating tenant schema" (::congregation/schema-name congregation))
    (-> (db/tenant-schema (::congregation/schema-name congregation))
        (.migrate)))

  (-> (Runtime/getRuntime)
      (.addShutdownHook (Thread. ^Runnable stop-app)))
  (log/info "Started"))

(defn -main [& args]
  (mount/start #'config/env)
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
      (migrations/migrate args (select-keys config/env [:database-url]))
      (System/exit 0))

    :else
    (start-app args)))
