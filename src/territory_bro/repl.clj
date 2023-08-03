;; Copyright © 2015-2023 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.repl
  (:require [clojure.pprint :refer [pp pprint]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [territory-bro.main :as main]))
#_pp #_pprint ; prevent the IDE from removing unused requires

(defn start []
  (main/start-app))

(defn stop []
  (main/stop-app))

(defn reset []
  (stop)
  (refresh :after 'territory-bro.repl/start))
