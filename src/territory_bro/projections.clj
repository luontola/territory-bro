;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.projections
  (:require [mount.core :as mount]
            [territory-bro.congregation :as congregation]
            [territory-bro.event-store :as event-store]
            [territory-bro.gis-user :as gis-user]))

(mount/defstate cache
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

(defn update-cache! [conn]
  (let [cached @cache
        updated (apply-new-events conn cached)]
    (when-not (identical? cached updated)
      ;; with concurrent requests, only one of them will update the cache
      (compare-and-set! cache cached updated))))

(defn current-state
  "Calculates the current state from all events, including uncommitted ones,
   but does not update the cache (it could cause dirty reads to others)."
  [conn]
  (:state (apply-new-events conn @cache)))

(comment
  (count (:state @cache))
  (update-cache! db/database))
