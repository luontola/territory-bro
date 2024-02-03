;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.resources
  (:import (java.net URL)))

(defn auto-refresh [*state load-resource]
  ;; TODO: implement detecting resource changes to clojure.tools.namespace.repl/refresh
  (let [{resource :resource, old-last-modified ::last-modified, :as state} @*state
        _ (assert (some? resource))
        new-last-modified (-> ^URL resource
                              (.openConnection)
                              (.getLastModified))]
    (::value (if (or (= old-last-modified new-last-modified)
                     ;; file was deleted temporarily
                     (zero? new-last-modified))
               state
               (reset! *state {:resource resource
                               ::value (load-resource resource)
                               ::last-modified new-last-modified})))))
