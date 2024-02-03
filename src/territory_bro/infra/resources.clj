;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.resources
  (:require [clojure.java.io :as io])
  (:import (java.net URL)))

(defn auto-refresh [*state load-resource]
  ;; TODO: implement detecting resource changes to clojure.tools.namespace.repl/refresh
  (let [{resource :resource, resource-path :resource-path, old-last-modified ::last-modified, :as state} @*state
        resource (cond
                   (some? resource) resource
                   (some? resource-path) (if-some [resource (io/resource resource-path)]
                                           resource
                                           (throw (IllegalStateException. (str "Resource not found: " resource-path))))
                   :else (throw (IllegalArgumentException. "Missing :resource and :resource-path")))
        new-last-modified (-> ^URL resource
                              (.openConnection)
                              (.getLastModified))]
    (::value (if (or (= old-last-modified new-last-modified)
                     ;; file was deleted temporarily
                     (and (zero? new-last-modified)
                          (some? (::value state))))
               state
               (reset! *state (assoc state
                                     ::value (load-resource resource)
                                     ::last-modified new-last-modified))))))
