(ns territory-bro.test.fixtures
  (:require [clojure.string :as str]
            [mount.core :as mount]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.resources :as resources]
            [territory-bro.projections :as projections])
  (:import (java.time Clock Instant ZoneOffset)))

(defn- delete-schemas-starting-with! [conn prefix]
  (doseq [schema (db/get-schemas conn)
          :when (str/starts-with? schema prefix)]
    (db/execute-one! conn [(str "DROP SCHEMA " schema " CASCADE")])))

(defn db-fixture [f]
  (mount/start #'config/env
               #'db/datasource
               #'projections/*cache
               #'resources/watcher)
  (let [schema (:database-schema config/env)]
    (assert (= "test_territorybro" schema)
            (str "Not the test database: " (pr-str schema)))
    ;; cleanup
    (db/with-transaction [conn {}]
      (delete-schemas-starting-with! conn schema))
    ;; setup
    (-> (db/master-schema schema)
        (.migrate))
    (-> (db/tenant-schema (str schema "_tenant") schema)
        (.migrate)))
  (f)
  (mount/stop))


(defn fixed-clock-fixture [^Instant fixed-time]
  (fn [f]
    (binding [config/*clock* (Clock/fixed fixed-time ZoneOffset/UTC)]
      (f))))


(def *last-command (atom nil))

(defn fake-dispatcher [_conn _state command]
  (reset! *last-command command)
  [])

(defn fake-dispatcher-fixture [f]
  (reset! *last-command nil)
  (binding [dispatcher/command! fake-dispatcher]
    (f))
  (reset! *last-command nil))


(defmacro with-fixtures [fixtures & body]
  `(let [fixture# (clojure.test/join-fixtures ~fixtures)]
     (fixture# (fn []
                 (try
                   ~@body
                   (catch Throwable e#
                     (clojure.test/do-report {:type :error
                                              :message "Uncaught exception, not in assertion."
                                              :expected nil
                                              :actual e#})))))))
