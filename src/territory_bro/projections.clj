;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.projections
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.poller :as poller])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

(mount/defstate *cache
  :start (atom {:last-event nil
                :state nil}))

(defn- update-projections [state event]
  (-> state
      (congregation/congregations-view event)
      (gis-user/gis-users-view event)))

(defn- apply-new-events [conn cached]
  (let [new-events (event-store/read-all-events conn {:since (:event/global-revision (:last-event cached))})
        last-event (last new-events)]
    (if last-event
      {:last-event last-event
       :state (reduce update-projections (:state cached) new-events)}
      cached)))

(defn refresh-now! [conn]
  (let [cached @*cache
        updated (apply-new-events conn cached)]
    (when-not (identical? cached updated)
      ;; with concurrent requests, only one of them will update the cache
      (when (compare-and-set! *cache cached updated)
        (log/info "Updated from revision" (:event/global-revision (:last-event cached))
                  "to" (:event/global-revision (:last-event updated)))))))

(defn cached-state []
  (:state @*cache))

(defn current-state
  "Calculates the current state from all events, including uncommitted ones,
   but does not update the cache (it could cause dirty reads to others)."
  [conn]
  (:state (apply-new-events conn @*cache)))


(mount/defstate refresher
  :start (poller/create (fn []
                          (db/with-db [conn {}]
                            (refresh-now! conn))))
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
  (refresh-now! db/database))
