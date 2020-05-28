;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.projections
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]
            [territory-bro.gis.db-admin :as db-admin]
            [territory-bro.gis.gis-change :as gis-change]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.gis.gis-user :as gis-user]
            [territory-bro.gis.gis-user-process :as gis-user-process]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.event-store :as event-store]
            [territory-bro.infra.executors :as executors]
            [territory-bro.infra.poller :as poller])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.time Duration)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

;;;; Cache

(mount/defstate *cache
  :start (atom {:last-event nil
                :state nil}))

(defn projection [state event]
  (-> state
      (card-minimap-viewport/projection event)
      (congregation-boundary/projection event)
      (congregation/projection event)
      (db-admin/projection event)
      (gis-change/projection event)
      (gis-user-process/projection event)
      (gis-user/projection event)
      (region/projection event)
      (share/projection event)
      (territory/projection event)))

(defn- apply-events [cache events]
  (update cache :state #(reduce projection % events)))

(defn- apply-new-events [cache conn]
  (let [new-events (event-store/read-all-events conn {:since (:event/global-revision (:last-event cache))})]
    (if (empty? new-events)
      cache
      (-> cache
          (apply-events new-events)
          (assoc :last-event (last new-events))))))

(defn cached-state []
  (:state @*cache))

(defn current-state
  "Calculates the current state from all events, including uncommitted ones,
   but does not update the cache (it could cause dirty reads to others)."
  [conn]
  (:state (apply-new-events @*cache conn)))


;;;; Refreshing projections

(defn- refresh-projections! []
  (let [[old new] (swap-vals! *cache (fn [cached]
                                       ;; Though this reads the database and is thus a slow
                                       ;; operation, retries on updating the atom should not
                                       ;; happen because it's called from a single thread.
                                       (db/with-db [conn {:read-only? true}]
                                         (apply-new-events cached conn))))]
    (when-not (identical? old new)
      (log/info "Updated from revision" (:event/global-revision (:last-event old))
                "to" (:event/global-revision (:last-event new))))))

(defn- run-process-managers! [state]
  (let [injections {:now (:now config/env)}
        commands (concat
                  (gis-user-process/generate-commands state injections)
                  (db-admin/generate-commands state injections))
        new-events (->> commands
                        (mapcat (fn [command]
                                  (db/with-db [conn {}]
                                    (dispatcher/command! conn state command))))
                        (doall))]
    (seq new-events)))

(defn- update-with-transient-events! [new-events]
  (when-some [transient-events (seq (filter :event/transient? new-events))]
    (swap! *cache apply-events transient-events)
    (log/info "Updated with" (count transient-events) "transient events")))

(defn- refresh-process-managers! []
  (when-some [new-events (run-process-managers! (cached-state))]
    (update-with-transient-events! new-events)
    ;; Some process managers produce persisted events which in turn
    ;; trigger other process managers (e.g. :congregation.event/gis-user-created),
    ;; so we have to refresh the projections and re-run the process managers
    ;; until there are no more commands to execute
    (refresh-projections!)
    (recur)))

(defn- startup-optimizations []
  (db/with-db [conn {:read-only? true}]
    (let [state (cached-state)
          injections {:get-present-schemas (fn []
                                             (gis-db/get-present-schemas conn {:schema-prefix ""}))
                      :get-present-users (fn []
                                           (gis-db/get-present-users conn {:username-prefix ""
                                                                           :schema-prefix ""}))}]
      (concat
       (db-admin/init-present-schemas state injections)
       (db-admin/init-present-users state injections)))))

(defn- refresh-startup-optimizations! []
  (let [new-events (startup-optimizations)]
    (update-with-transient-events! new-events)))

(defn refresh! []
  (log/info "Refreshing projections")
  (refresh-projections!)
  (refresh-process-managers!))

(defn refresh-on-startup! []
  (log/info "Refreshing projections on startup")
  (refresh-projections!)
  (refresh-startup-optimizations!)
  (refresh-process-managers!))

(mount/defstate refresher
  :start (poller/create refresh!)
  :stop (poller/shutdown! refresher))

(defn refresh-async! []
  (poller/trigger! refresher))

(defn await-refreshed [^Duration duration]
  (poller/await refresher duration))


(defonce ^:private scheduled-refresh-thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.projections/scheduled-refresh-%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler executors/uncaught-exception-handler)
      (.build)))

(mount/defstate scheduled-refresh
  :start (doto (Executors/newScheduledThreadPool 1 scheduled-refresh-thread-factory)
           (.scheduleWithFixedDelay (executors/safe-task refresh-async!)
                                    0 1 TimeUnit/MINUTES))
  :stop (.shutdown ^ScheduledExecutorService scheduled-refresh))


(comment
  (count (:state @*cache))
  (refresh-async!)
  (refresh!))
