;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.projections
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.event-store :as event-store]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.poller :as poller])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

;;; Cache

(mount/defstate *cache
  :start (atom {:last-event nil
                :state nil}))

(defn- update-projections [state event]
  (-> state
      (congregation/congregations-view event)
      (gis-user/gis-users-view event)
      (db-admin/projection event)))

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


;;; Refreshing

(def db-admin-injections
  {:migrate-tenant-schema! db/migrate-tenant-schema!
   :ensure-gis-user-present! (fn [args]
                               (db/with-db [conn {}]
                                 (gis-user/ensure-present! conn args)))
   :ensure-gis-user-absent! (fn [args]
                              (db/with-db [conn {}]
                                (gis-user/ensure-absent! conn args)))})

(defn- run-process-managers! [state]
  (let [*transient-events (atom [])
        dispatch-transient-event! (fn dispatch-transient-event! [event]
                                    (assert (:event/transient? event) {:event event})
                                    (swap! *transient-events conj event))
        db-admin-injections (assoc db-admin-injections :dispatch! dispatch-transient-event!)]

    (db-admin/process-pending-changes! state db-admin-injections)

    (seq @*transient-events)))

(defn refresh-now! []
  (let [[old new] (swap-vals! *cache (fn [cached]
                                       ;; Though this reads the database and is thus a slow
                                       ;; operation, retries on updating the atom should not
                                       ;; happen because it's called from a single thread.
                                       (db/with-db [conn {:read-only? true}]
                                         (apply-new-events cached conn))))]
    (when-not (identical? old new)
      (log/info "Updated from revision" (:event/global-revision (:last-event old))
                "to" (:event/global-revision (:last-event new)))))

  (when-some [transient-events (run-process-managers! (cached-state))]
    (swap! *cache apply-events transient-events)
    (log/info "Updated with" (count transient-events) "transient events")
    (recur)))

(mount/defstate refresher
  :start (poller/create (fn []
                          (refresh-now!)))
  :stop (poller/shutdown! refresher))

(defn refresh-async! []
  (poller/trigger! refresher))

(defn await-refreshed []
  (poller/await refresher))


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
  (refresh-now!))
