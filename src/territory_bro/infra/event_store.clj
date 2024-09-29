;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.event-store
  (:require [territory-bro.events :as events]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db])
  (:import (java.util UUID)
           (org.postgresql.util PSQLException)
           (territory_bro WriteConflictException)))

(def ^:dynamic *event->json* events/event->json)
(def ^:dynamic *json->event* events/json->event)

(def ^:private queries (db/compile-queries "db/hugsql/event-store.sql"))

(defn- sorted-keys [event]
  ;; sorted maps are slower to access than hash maps,
  ;; so we use them in only dev mode for readability
  (if (:dev config/env)
    (events/sorted-keys event)
    event))

(defn- parse-db-row [row]
  (-> (:data row)
      *json->event*
      (assoc :event/stream-id (:stream_id row))
      (assoc :event/stream-revision (:stream_revision row))
      (assoc :event/global-revision (:global_revision row))
      (sorted-keys)))

(defn read-stream
  ([conn stream-id]
   (read-stream conn stream-id {}))
  ([conn stream-id {:keys [since]}]
   (assert (some? stream-id))
   (->> (db/query! conn queries :read-stream {:stream stream-id
                                              :since (or since 0)})
        (mapv parse-db-row))))

(defn read-all-events
  ([conn]
   (read-all-events conn {}))
  ([conn {:keys [since]}]
   (->> (db/query! conn queries :read-all-events {:since (or since 0)})
        (mapv parse-db-row))))

(defn stream-exists? [conn stream-id]
  (not (empty? (db/query! conn queries :find-stream {:stream stream-id}))))

(defn ^:dynamic check-new-stream [conn stream-id]
  (when (stream-exists? conn stream-id)
    (throw (WriteConflictException. (str "Event stream " stream-id " already exists")))))

(defn- save-event! [conn stream-id stream-revision event]
  (try
    (db/query! conn queries :save-event {:stream stream-id
                                         :stream_revision stream-revision
                                         :data (-> event *event->json*)})
    (catch PSQLException e
      (if (= db/psql-serialization-failure (.getSQLState e))
        (throw (WriteConflictException.
                (str "Failed to save stream " stream-id
                     " revision " stream-revision
                     ": " (pr-str event))
                e))
        (throw e)))))

(defn save! [conn stream-id stream-revision events]
  ;; The prepare_new_event trigger already locks the events table, but
  ;; transaction conflicts still happen when generating multiple QR codes
  ;; in parallel. Maybe the insert statement acquires a lower level lock
  ;; before the trigger is executed, and that produces a deadlock?
  (db/query! conn queries :lock-events-table-for-writing)
  (->> events
       (map-indexed
        (fn [idx event]
          (assert (not (:event/transient? event))
                  {:event event})
          (let [next-revision (when stream-revision
                                (+ 1 idx stream-revision))
                result (save-event! conn stream-id next-revision event)]
            (-> event
                (assoc :event/stream-id stream-id)
                (assoc :event/stream-revision (:stream_revision result))
                (assoc :event/global-revision (:global_revision result))
                (sorted-keys)))))
       (doall)))

(defn stream-info [conn stream-id]
  (db/execute-one! conn ["select * from stream where stream_id = ?"
                         stream-id]))

(comment
  (db/with-db [conn {:read-only? true}]
    (read-stream conn (UUID/fromString "61e51981-bbd3-4298-a7a6-46109e39dd52")))
  (db/with-db [conn {:read-only? true}]
    (take-last 10 (read-all-events conn))))
