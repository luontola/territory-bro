;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.poller
  (:refer-clojure :exclude [await])
  (:require [territory-bro.infra.executors :as executors])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.time Duration)
           (java.util Queue)
           (java.util.concurrent ExecutorService Executors TimeUnit ArrayBlockingQueue Future)))

(defprotocol Poller
  (trigger! [this])
  (await [this ^Duration timeout])
  (shutdown! [this]))

(defrecord AsyncPoller [^Queue available-tasks
                        ^ExecutorService executor]
  Poller
  (trigger! [_]
    (when-let [task (.poll available-tasks)]
      (.execute executor (executors/safe-task
                          (fn []
                            (.add available-tasks task)
                            (task))))))

  (await [_ timeout]
    (let [^Duration timeout timeout
          ^Future future (.submit executor ^Runnable (fn []))]
      (.get future (.toNanos timeout) TimeUnit/NANOSECONDS)))

  (shutdown! [_]
    (doto executor
      (.shutdown)
      (.awaitTermination 1 TimeUnit/MINUTES)
      (.shutdownNow))))

(defonce ^:private thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.infra.poller/%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler executors/uncaught-exception-handler)
      (.build)))

(defn create [task]
  (assert (fn? task) {:task task})
  (AsyncPoller. (doto (ArrayBlockingQueue. 1)
                  (.add task))
                (Executors/newFixedThreadPool 1 thread-factory)))
