; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.testing
  (:require [clojure.test :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [territory-bro.config :refer [env]]
            [conman.core :as conman]
            [territory-bro.db :as db]
            [clojure.java.jdbc :as jdbc]))

(defn api-fixture [f]
  (mount/start
   #'territory-bro.config/env
   #'territory-bro.db/*db*
   #'territory-bro.handler/app)
  (migrations/migrate ["migrate"] (select-keys env [:database-url]))
  (f)
  (mount/stop))

(defn transaction-rollback-fixture [f]
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (f)))
