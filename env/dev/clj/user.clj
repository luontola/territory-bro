; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns user
  (:require [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh]]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [territory-bro.config :refer [env]]
            [territory-bro.db]
            [territory-bro.main :refer [start-app]]))

(defn start []
  (mount/start-without #'territory-bro.main/repl-server))

(defn stop []
  (mount/stop-except #'territory-bro.main/repl-server))

(defn restart []
  (stop)
  (start))

(defn restart-db []
  (mount/stop #'territory-bro.db/*db*)
  (mount/start #'territory-bro.db/*db*)
  (binding [*ns* 'territory-bro.db]
    (conman/bind-connection territory-bro.db/*db* "sql/queries.sql")))

(defn reset-db []
  (migrations/migrate ["reset"] (select-keys env [:database-url])))

(defn migrate []
  (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn rollback []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration [name]
  (migrations/create name (select-keys env [:database-url])))

(defn run-all-tests []
  (refresh)
  (test/run-all-tests #"territory-bro\..*"))
