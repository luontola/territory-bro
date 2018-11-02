; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.testing
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [territory-bro.config :refer [env]]
            [territory-bro.db :as db]
            [territory-bro.handler])
  (:import (java.util.regex Pattern)))

(defn api-fixture [f]
  (mount/start
   #'territory-bro.config/env
   #'territory-bro.db/databases
   #'territory-bro.handler/app)
  (migrations/migrate ["migrate"] (select-keys env [:database-url]))
  (db/as-tenant nil
    (f))
  (mount/stop))

(defn transaction-rollback-fixture [f]
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (f)))

(defn re-equals [s]
  (re-pattern (clojure.core/str "^" (Pattern/quote s) "$")))
