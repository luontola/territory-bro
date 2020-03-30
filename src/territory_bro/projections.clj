;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.projections
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [territory-bro.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.congregation-boundary :as congregation-boundary]
            [territory-bro.db :as db]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.event-store :as event-store]
            [territory-bro.executors :as executors]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-sync :as gis-sync]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.gis-user-process :as gis-user-process]
            [territory-bro.poller :as poller]
            [territory-bro.subregion :as subregion]
            [territory-bro.territory :as territory])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.time Duration)
           (java.util UUID)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

;;;; Cache

(mount/defstate *cache
  :start (atom {:last-event nil
                :state nil}))

(defn- update-projections [state event]
  (-> state
      (card-minimap-viewport/projection event)
      (congregation-boundary/projection event)
      (congregation/projection event)
      (db-admin/projection event)
      (gis-sync/projection event)
      (gis-user-process/projection event)
      (gis-user/projection event)
      (subregion/projection event)
      (territory/projection event)))

(defn- apply-events [cache events]
  (update cache :state #(reduce update-projections % events)))

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

(defn- run-process-managers! [state]
  (let [commands (concat
                  (gis-user-process/generate-commands state {:now (:now config/env)})
                  (db-admin/generate-commands state {:now (:now config/env)}))
        new-events (->> commands
                        (mapcat (fn [command]
                                  (db/with-db [conn {}]
                                    (dispatcher/command! conn state command))))
                        (doall))]
    (seq new-events)))

(defn refresh! []
  (log/info "Refreshing projections")
  (let [[old new] (swap-vals! *cache (fn [cached]
                                       ;; Though this reads the database and is thus a slow
                                       ;; operation, retries on updating the atom should not
                                       ;; happen because it's called from a single thread.
                                       (db/with-db [conn {:read-only? true}]
                                         (apply-new-events cached conn))))]
    (when-not (identical? old new)
      (log/info "Updated from revision" (:event/global-revision (:last-event old))
                "to" (:event/global-revision (:last-event new)))))

  (when-some [new-events (run-process-managers! (cached-state))]
    (when-some [transient-events (seq (filter :event/transient? new-events))]
      (swap! *cache apply-events transient-events)
      (log/info "Updated with" (count transient-events) "transient events"))
    (recur)))

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
                                    0 60 TimeUnit/SECONDS))
  :stop (.shutdown ^ScheduledExecutorService scheduled-refresh))


(comment
  (count (:state @*cache))
  (refresh-async!)
  (refresh!))


;;;; GIS sync

;; TODO: extract to a new namespace?
(defn refresh-gis-changes!
  ([]
   (log/info "Refreshing GIS changes")
   (db/with-db [conn {}]
     (refresh-gis-changes! conn (cached-state)))
   ;; TODO: refresh only if there were some changes
   (refresh-async!))
  ([conn state]
   (let [change (gis-db/next-unprocessed-change conn)]
     (when change
       (let [new-id (when (= :INSERT (:gis-change/op change))
                      (when (nil? (:gis-change/replacement-id change)) ; the replacement ID should already be unused, and replacing it a second time would bring chaos (e.g. infinite loop)
                        (:id (:gis-change/new change))))]
         (if (and new-id (event-store/stream-exists? conn new-id))
           (let [replacement-id (UUID/randomUUID)
                 {:gis-change/keys [schema table]} change]
             (log/info "Replacing" (str schema "." table) "ID" new-id "with" replacement-id)
             (assert (not (event-store/stream-exists? conn replacement-id))
                     {:replacement-id replacement-id})
             (gis-db/replace-id! conn schema table new-id replacement-id)
             (recur conn state))
           (let [command (gis-sync/change->command change state)
                 events (dispatcher/command! conn state command)
                 ;; the state needs to be updated for e.g. command validation's foreign key checks
                 state (reduce update-projections state events)]
             (gis-db/mark-changes-processed! conn [(:gis-change/id change)])
             ;; TODO: commit between every change?
             (recur conn state))))))))

(mount/defstate gis-refresher
  :start (poller/create refresh-gis-changes!)
  :stop (poller/shutdown! gis-refresher))

(defn refresh-gis-async! []
  (poller/trigger! gis-refresher))

(defn await-gis-refreshed [^Duration duration]
  (poller/await gis-refresher duration))


(defonce ^:private scheduled-gis-refresh-thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.projections/scheduled-gis-refresh-%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler executors/uncaught-exception-handler)
      (.build)))

(mount/defstate scheduled-gis-refresh
  :start (doto (Executors/newScheduledThreadPool 1 scheduled-gis-refresh-thread-factory)
           (.scheduleWithFixedDelay (executors/safe-task refresh-gis-async!)
                                    0 5 TimeUnit/MINUTES))
  :stop (.shutdown ^ScheduledExecutorService scheduled-gis-refresh))


(defonce ^:private notified-gis-refresh-thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.projections/notified-gis-refresh-%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler executors/uncaught-exception-handler)
      (.build)))

(mount/defstate notified-gis-refresh
  :start (doto (Executors/newFixedThreadPool 1 notified-gis-refresh-thread-factory)
           (.submit (executors/safe-task #(gis-db/listen-for-gis-changes refresh-gis-async!))))
  :stop (.shutdownNow ^ScheduledExecutorService notified-gis-refresh))
