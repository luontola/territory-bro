(ns territory-bro.infra.executors
  (:require [clojure.tools.logging :as log])
  (:import (java.lang Thread$UncaughtExceptionHandler)))

(def uncaught-exception-handler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_this _thread exception]
     ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
      (log/error exception (str "Uncaught exception\n" (pr-str exception))))))

(defn run-safely! [task]
  (try
    (task)
    (catch InterruptedException _
      (.interrupt (Thread/currentThread)))
    (catch Throwable e
      (let [t (Thread/currentThread)]
        (if-let [handler (.getUncaughtExceptionHandler t)]
          (.uncaughtException handler t e)
          (.printStackTrace e))))))

(defn safe-task ^Callable [task]
  (fn []
    (run-safely! task)))
