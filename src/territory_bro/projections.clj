;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.projections
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.event-store :as event-store]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-sync :as gis-sync]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.gis-user-process :as gis-user-process]
            [territory-bro.poller :as poller]
            [territory-bro.territory :as territory])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.time Duration)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

;;;; Cache

(mount/defstate *cache
  :start (atom {:last-event nil
                :state nil}))

(defn- update-projections [state event]
  (-> state
      (congregation/projection event)
      (db-admin/projection event)
      (gis-sync/projection event)
      (gis-user-process/projection event)
      (gis-user/projection event)
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


(mount/defstate scheduled-refresh
  :start (doto (Executors/newScheduledThreadPool 1 (-> (ThreadFactoryBuilder.)
                                                       (.setNameFormat "territory-bro.projections/scheduled-refresh")
                                                       (.setDaemon true)
                                                       (.build)))
           (.scheduleWithFixedDelay refresh-async! 0 60 TimeUnit/SECONDS))
  :stop (.shutdown ^ScheduledExecutorService scheduled-refresh))


(comment
  (count (:state @*cache))
  (refresh-async!)
  (refresh!))


;;;; GIS sync

(defn sync-gis-changes! [conn]
  ;; TODO: get only unprocessed changes
  (let [state (cached-state)
        changes (gis-db/get-changes conn)
        commands (map #(gis-sync/change->command % state) changes)
        ;; TODO: process all changes
        command (first commands)]
    ;; TODO: mark change as processed
    ;; TODO: refresh state between every command?
    ;; TODO: handle conflicting stream IDs
    (dispatcher/command! conn state command)))
