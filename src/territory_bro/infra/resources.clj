;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.resources
  (:require [clojure.java.io :as io])
  (:import (java.io IOException)
           (java.net URL)))

(defn- invalid-resource [resource]
  (IllegalArgumentException. (str "Resource must be an URL or string: " (pr-str resource))))

(defn init-state [resource]
  (when (nil? resource)
    (throw (invalid-resource resource)))
  (atom {::resource resource}))

(defn- last-modified [^URL resource]
  (let [connection (.openConnection resource)
        result (.getLastModified connection)]
    (try
      ;; URLConnection doesn't expose a close() method, but its resources
      ;; can be released by closing the InputStream
      (.close (.getInputStream connection))
      (catch IOException _))
    result))

(defn auto-refresh! [*state load-resource]
  ;; TODO: implement detecting resource changes to clojure.tools.namespace.repl/refresh
  (let [{::keys [resource value], old-last-modified ::last-modified} @*state
        resource (cond
                   (instance? URL resource) resource
                   (string? resource) (if-some [resource (io/resource resource)]
                                        resource
                                        (throw (IllegalStateException. (str "Resource not found: " resource))))
                   :else (throw (invalid-resource resource)))
        new-last-modified (last-modified resource)]
    (if (or (= old-last-modified new-last-modified)
            ;; file was deleted (temporarily), reuse the old value
            (and (zero? new-last-modified)
                 (some? old-last-modified)))
      value
      (::value (reset! *state {::resource resource
                               ::value (load-resource resource)
                               ::last-modified new-last-modified})))))

(defn auto-refresher [resource load-resource]
  (let [*state (init-state resource)]
    (fn []
      (auto-refresh! *state load-resource))))
