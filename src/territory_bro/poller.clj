;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.poller
  (:refer-clojure :exclude [await])
  (:require [clojure.tools.logging :as log])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.lang Thread$UncaughtExceptionHandler)
           (java.util Queue)
           (java.util.concurrent ExecutorService Executors TimeUnit ArrayBlockingQueue)))

(defprotocol Poller
  (trigger! [this])
  (await [this])
  (shutdown! [this]))

(def ^:private uncaught-exception-handler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_this _thread exception]
     ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
      (log/error exception (str "Uncaught exception in worker thread\n" (pr-str exception))))))

(def ^:private thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.poller/%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler uncaught-exception-handler)
      (.build)))

(defn- run-safely! [task]
  (try
    (task)
    (catch InterruptedException _
      (.interrupt (Thread/currentThread)))
    (catch Throwable e
      (let [t (Thread/currentThread)]
        (when-let [handler (.getUncaughtExceptionHandler t)]
          (.uncaughtException handler t e))))))

(defrecord AsyncPoller [^Queue available-tasks
                        ^ExecutorService executor]
  Poller
  (trigger! [_]
    (when-let [task (.poll available-tasks)]
      (.execute executor (fn []
                           (.add available-tasks task)
                           (run-safely! task)))))

  (await [_]
    (let [future (.submit executor ^Runnable (fn []))]
      (.get future)))

  (shutdown! [_]
    (doto executor
      (.shutdown)
      (.awaitTermination 1 TimeUnit/MINUTES)
      (.shutdownNow))))

(defn create [task]
  (assert (fn? task) {:task task})
  (AsyncPoller. (doto (ArrayBlockingQueue. 1)
                  (.add task))
                (Executors/newFixedThreadPool 1 thread-factory)))
