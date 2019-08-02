;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.poller-test
  (:require [clojure.test :refer :all]
            [territory-bro.poller :as poller])
  (:import (ch.qos.logback.classic Logger)
           (ch.qos.logback.classic.spi LoggingEvent)
           (ch.qos.logback.core.read ListAppender)
           (java.util.concurrent CountDownLatch TimeUnit CyclicBarrier)
           (org.slf4j LoggerFactory)))

(defn- await-latch [^CountDownLatch latch]
  (.await latch 1 TimeUnit/SECONDS))

(defn- await-barrier [^CyclicBarrier barrier]
  (.await barrier 1 TimeUnit/SECONDS))

(deftest poller-test
  (testing "runs the task when triggered"
    (let [task-finished (CountDownLatch. 1)
          task-count (atom 0)
          task-thread (atom nil)
          p (poller/create (fn []
                             (swap! task-count inc)
                             (reset! task-thread (Thread/currentThread))))]
      (poller/trigger! p)
      (await-latch task-finished)
      (poller/shutdown! p)

      (is (= 1 @task-count) "task count")
      (is (instance? Thread @task-thread))
      (is (not= (Thread/currentThread) @task-thread)
          "runs the task in a background thread")))

  (testing "reruns when triggered after task started"
    (let [one-task-started (CountDownLatch. 1)
          two-tasks-started (CountDownLatch. 2)
          task-count (atom 0)
          p (poller/create (fn []
                             (swap! task-count inc)
                             (.countDown one-task-started)
                             (.countDown two-tasks-started)))]
      (poller/trigger! p)
      (await-latch one-task-started)
      (poller/trigger! p)
      (await-latch two-tasks-started)
      (poller/shutdown! p)

      (is (= 2 @task-count) "task count")))

  (testing "reruns at most once when triggered after task started many times"
    (let [one-task-started (CountDownLatch. 1)
          many-tasks-triggered (CountDownLatch. 1)
          two-tasks-finished (CountDownLatch. 2)
          task-count (atom 0)
          p (poller/create (fn []
                             (swap! task-count inc)
                             (.countDown one-task-started)
                             (await-latch many-tasks-triggered)
                             (.countDown two-tasks-finished)))]
      (poller/trigger! p)
      (await-latch one-task-started)
      (poller/trigger! p)
      (poller/trigger! p)
      (poller/trigger! p)
      (poller/trigger! p)
      (.countDown many-tasks-triggered)
      (await-latch two-tasks-finished)
      (poller/shutdown! p)

      (is (= 2 @task-count) "task count")))

  (testing "reruns many times when triggered after task finished"
    (let [task-finished (CyclicBarrier. 2)
          task-count (atom 0)
          p (poller/create (fn []
                             (swap! task-count inc)
                             (await-barrier task-finished)))]
      (poller/trigger! p)
      (await-barrier task-finished)
      (poller/trigger! p)
      (await-barrier task-finished)
      (poller/trigger! p)
      (await-barrier task-finished)
      (poller/shutdown! p)

      (is (= 3 @task-count) "task count")))

  (testing "runs only a single task at a time"
    (let [concurrent-tasks (atom 0)
          max-concurrent-tasks (atom 0)
          task-count (atom 0)
          timeout (+ 1000 (System/currentTimeMillis))
          p (poller/create (fn []
                             (swap! concurrent-tasks inc)
                             (swap! max-concurrent-tasks #(max % @concurrent-tasks))
                             (swap! task-count inc)
                             (Thread/yield)
                             (swap! concurrent-tasks dec)))]
      (while (and (> 10 @task-count)
                  (> timeout (System/currentTimeMillis)))
        (poller/trigger! p))
      (poller/shutdown! p)

      (is (< 1 @task-count) "task count")
      (is (= 1 @max-concurrent-tasks) "max concurrent tasks")))

  (testing "logs uncaught exceptions"
    (let [appender (doto (ListAppender.)
                     (.start))
          logger (doto ^Logger (LoggerFactory/getLogger "territory-bro.poller")
                   (.addAppender appender))
          p (poller/create (fn []
                             (throw (RuntimeException. "dummy"))))]
      (poller/trigger! p)
      (poller/shutdown! p)
      (.detachAppender logger appender)

      (is (= 1 (count (.-list appender))) "log event count")
      (let [event ^LoggingEvent (first (.-list appender))]
        (is (= "Uncaught exception in worker thread" (.getMessage event)))
        (is (= "dummy" (.getMessage (.getThrowableProxy event))))))))
