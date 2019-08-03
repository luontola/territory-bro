;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.poller
  (:require [clojure.tools.logging :as log])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.lang Thread$UncaughtExceptionHandler)
           (java.util Queue)
           (java.util.concurrent ExecutorService Executors TimeUnit ArrayBlockingQueue)))

(def ^:private uncaught-exception-handler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_this _thread exception]
      (log/error exception "Uncaught exception in worker thread"))))

(def ^:private thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.poller/%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler uncaught-exception-handler)
      (.build)))

(defn create [task]
  (assert (fn? task) {:task task})
  {::available-tasks (doto (ArrayBlockingQueue. 1)
                       (.add task))
   ::executor (Executors/newFixedThreadPool 1 thread-factory)})

(defn- run-safely! [task]
  (try
    (task)
    (catch InterruptedException _
      (.interrupt (Thread/currentThread)))
    (catch Throwable e
      (let [t (Thread/currentThread)]
        (when-let [handler (.getUncaughtExceptionHandler t)]
          (.uncaughtException handler t e))))))

(defn trigger! [this]
  (let [executor ^ExecutorService (::executor this)
        available-tasks ^Queue (::available-tasks this)
        task (.poll available-tasks)]
    (when task
      (.execute executor (fn []
                           (.add available-tasks task)
                           (run-safely! task))))))

(defn shutdown! [this]
  (doto ^ExecutorService (::executor this)
    (.shutdown)
    (.awaitTermination 1 TimeUnit/MINUTES)
    (.shutdownNow)))

(defn await [this]
  (let [executor ^ExecutorService (::executor this)
        future (.submit executor ^Runnable (fn []))]
    (.get future)))
