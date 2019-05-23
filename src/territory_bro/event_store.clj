;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.event-store
  (:require [territory-bro.db :as db]
            [territory-bro.events :as events]))

(def ^:dynamic *event->json* events/event->json)
(def ^:dynamic *json->event* events/json->event)

(def ^:private query! (db/compile-queries "db/hugsql/event-store.sql"))

(defn- coerce-keyword [s]
  (if (string? s)
    (keyword s)
    s))

(defn- parse-db-row [row]
  (-> (:data row)
      *json->event*
      (assoc :event/stream-id (:stream_id row)
             :event/stream-revision (:stream_revision row)
             :event/global-revision (:global_revision row))
      (update :event/type coerce-keyword)))

(defn read-stream
  ([conn stream-id]
   (read-stream conn stream-id {}))
  ([conn stream-id opts]
   (->> (query! conn :read-stream {:stream stream-id
                                   :since (get opts :since 0)})
        (map parse-db-row)
        (doall))))

(defn read-all-events
  ([conn]
   (read-all-events conn {}))
  ([conn opts]
   (->> (query! conn :read-all-events {:since (get opts :since 0)})
        (map parse-db-row)
        (doall))))

(defn save! [conn stream-id stream-revision events]
  (->> events
       (map-indexed
        (fn [idx event]
          (let [next-revision (+ 1 idx stream-revision)
                [result] (query! conn :save-event {:stream stream-id
                                                   :stream_revision next-revision
                                                   :data (-> event *event->json*)})]
            (:global_revision result))))
       (last)))
