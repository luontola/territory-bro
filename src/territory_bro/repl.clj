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
