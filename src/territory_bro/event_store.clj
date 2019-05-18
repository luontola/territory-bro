;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.event-store
  (:require [territory-bro.db :as db]))

(def ^:private query! (db/compile-queries "db/hugsql/event-store.sql"))

(defn- format-event [row]
  (assoc (:data row)
         :event/stream-id (:stream_id row)
         :event/stream-revision (:stream_revision row)
         :event/global-revision (:global_revision row)
         :event/type (keyword (:event/type (:data row)))))

(defn read-stream
  ([conn stream-id]
   (read-stream conn stream-id {}))
  ([conn stream-id opts]
   (->> (query! conn :read-stream {:stream stream-id
                                   :since (get opts :since 0)})
        (map format-event)
        (doall))))

(defn read-all-events
  ([conn]
   (read-all-events conn {}))
  ([conn opts]
   (->> (query! conn :read-all-events {:since (get opts :since 0)})
        (map format-event)
        (doall))))

(defn save! [conn stream-id stream-revision events]
  (->> events
       (map-indexed
        (fn [idx event]
          (let [next-revision (+ 1 idx stream-revision)
                [result] (query! conn :save-event {:stream stream-id
                                                   :stream_revision next-revision
                                                   :data event})]
            (:global_revision result))))
       (last)))
