;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.event-store
  (:require [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.events :as events])
  (:import (java.util UUID)
           (org.postgresql.util PSQLException)
           (territory_bro WriteConflictException)))

(def ^:dynamic *event->json* events/event->json)
(def ^:dynamic *json->event* events/json->event)

(def ^:private query! (db/compile-queries "db/hugsql/event-store.sql"))

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
   (->> (query! conn :read-stream {:stream stream-id
                                   :since (or since 0)})
        (map parse-db-row)
        (doall))))

(defn read-all-events
  ([conn]
   (read-all-events conn {}))
  ([conn {:keys [since]}]
   (->> (query! conn :read-all-events {:since (or since 0)})
        (map parse-db-row)
        (doall))))

(defn- save-event! [conn stream-id stream-revision event]
  (try
    (first (query! conn :save-event {:stream stream-id
                                     :stream_revision stream-revision
                                     :data (-> event *event->json*)}))
    (catch PSQLException e
      (if (= db/psql-serialization-failure (.getSQLState e))
        (throw (WriteConflictException.
                (str "Failed to save stream " stream-id
                     " revision " stream-revision
                     ": " (pr-str event))
                e))
        (throw e)))))

(defn save! [conn stream-id stream-revision events]
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

(defn check-event-stream-does-not-exist [conn stream-id]
  (let [events (read-stream conn stream-id)]
    (when (not (empty? events))
      (throw (WriteConflictException. (str "Event stream " stream-id " already exists"))))))

(comment
  (db/with-db [conn {:read-only? true}]
    (read-stream conn (UUID/fromString "61e51981-bbd3-4298-a7a6-46109e39dd52")))
  (db/with-db [conn {:read-only? true}]
    (take-last 10 (read-all-events conn))))
