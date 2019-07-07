;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns user
  (:require [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh]]
            [mount.core :as mount]
            [territory-bro.config :refer [env]]
            [territory-bro.db]
            [territory-bro.main :refer [start-app]]))

(defn start []
  (mount/start-without #'territory-bro.main/repl-server))

(defn stop []
  (mount/stop-except #'territory-bro.main/repl-server))

(defn restart []
  (stop)
  (start))

(defn run-all-tests []
  (refresh)
  (test/run-all-tests #"territory-bro\..*"))
