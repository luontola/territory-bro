;; Copyright © 2015-2021 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.gis-sync
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.gis.gis-change :as gis-change]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.executors :as executors]
            [territory-bro.infra.poller :as poller]
            [territory-bro.projections :as projections])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.time Duration)
           (java.util.concurrent Executors ExecutorService TimeUnit)
           (org.postgresql PGConnection)
           (org.postgresql.util PSQLException)))

(defn- process-changes! [conn state had-changes?]
  (if-some [change (gis-db/next-unprocessed-change conn)]
    (let [changes (gis-change/normalize-change change)
          state (reduce (fn [state change]
                          (let [command (gis-change/change->command change state)
                                events (dispatcher/command! conn state command)]
                            ;; the state needs to be updated for e.g. command validation's foreign key checks
                            (reduce projections/projection state events)))
                        state
                        changes)]
      (gis-db/mark-changes-processed! conn [(:gis-change/id change)])
      (recur conn state true))
    had-changes?))

(defn refresh! []
  (log/info "Refreshing GIS changes")
  (when (db/with-db [conn {}]
          (process-changes! conn (projections/cached-state) false))
    (projections/refresh-async!)))

(mount/defstate refresher
  :start (poller/create refresh!)
  :stop (poller/shutdown! refresher))

(defn refresh-async! []
  (poller/trigger! refresher))

(defn await-refreshed [^Duration duration]
  (poller/await refresher duration))


(defonce ^:private scheduled-refresh-thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.gis.gis-sync/scheduled-refresh-%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler executors/uncaught-exception-handler)
      (.build)))

(mount/defstate scheduled-refresh
  :start (doto (Executors/newScheduledThreadPool 1 scheduled-refresh-thread-factory)
           (.scheduleWithFixedDelay (executors/safe-task refresh-async!)
                                    0 1 TimeUnit/MINUTES))
  :stop (.shutdown ^ExecutorService scheduled-refresh))


(defn listen-for-gis-changes [notify]
  (jdbc/with-db-connection [conn db/database {}]
    (db/use-master-schema conn)
    (jdbc/execute! conn ["LISTEN gis_change"])
    (let [timeout (Duration/ofSeconds 30)
          ^PGConnection pg-conn (-> (jdbc/db-connection conn)
                                    (.unwrap PGConnection))]
      (log/info "Started listening for GIS changes")
      (notify)
      (loop []
        ;; getNotifications is not interruptible, so it will take up to `timeout` for this loop to exit
        (let [notifications (try
                              (.getNotifications pg-conn (.toMillis timeout))
                              (catch PSQLException e
                                ;; hide failures during shutdown; the database connection was closed while waiting for notifications
                                (if (.isInterrupted (Thread/currentThread))
                                  (log/info "Ignoring errors during shutdown:" (str e))
                                  (throw e))))]
          (when-not (.isInterrupted (Thread/currentThread))
            (doseq [_ notifications]
              (notify))
            (recur))))
      (log/info "Stopped listening for GIS changes"))))

(defn- until-interrupted [task]
  (fn []
    (while (not (.isInterrupted (Thread/currentThread)))
      (task))))

(defonce ^:private notified-refresh-thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.gis.gis-sync/notified-refresh-%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler executors/uncaught-exception-handler)
      (.build)))

(mount/defstate notified-refresh
  :start (doto (Executors/newFixedThreadPool 1 notified-refresh-thread-factory)
           (.submit ^Callable (until-interrupted
                               (executors/safe-task
                                #(listen-for-gis-changes refresh-async!)))))
  ;; XXX: not awaiting termination, because PGConnection.getNotifications() is not interruptible
  :stop (.shutdownNow ^ExecutorService notified-refresh))
