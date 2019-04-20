;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.testing
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [territory-bro.config :as config :refer [env]]
            [territory-bro.db :as db]
            [territory-bro.router :as handler])
  (:import (java.util.regex Pattern)))

(defn api-fixture [f]
  (mount/stop) ; during interactive development, app might be running when tests start
  (mount/start #'config/env
               #'db/databases
               #'handler/app)
  (migrations/migrate ["migrate"] (select-keys env [:database-url]))
  (db/as-tenant nil
    (f))
  (mount/stop))

(defn transaction-rollback-fixture [f]
  (conman/with-transaction [db/*conn* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*conn*)
    (f)))

(defn re-equals [^String s]
  (re-pattern (str "^" (Pattern/quote s) "$")))
