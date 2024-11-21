(ns territory-bro.infra.resources
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [nextjournal.beholder :as beholder])
  (:import (java.net URL)))

(def ^:private *change-counter (atom 0))

(defn notify-change! []
  (swap! *change-counter inc)
  nil)

(mount/defstate watcher
  :start (do
           (notify-change!)
           ;; Watches for changed resources when running locally in dev mode.
           ;; The directories won't exist in prod, so this will then do nothing.
           (beholder/watch notify-change! "resources" "target/web-dist"))
  :stop (beholder/stop watcher))


(defn- invalid-resource [resource]
  (IllegalArgumentException. (str "Resource must be an URL or string: " (pr-str resource))))

(defn init-state [resource]
  (when (nil? resource)
    (throw (invalid-resource resource)))
  (atom {::resource resource}))

(defn- resolve-resource [resource]
  (cond
    (instance? URL resource) resource
    (string? resource) (if-some [resource (io/resource resource)]
                         resource
                         (throw (IllegalStateException. (str "Resource not found: " resource))))
    :else (throw (invalid-resource resource))))

(defn auto-refresh! [*state load-resource]
  ;; TODO: implement detecting resource changes to clojure.tools.namespace.repl/refresh
  (let [state @*state
        old-last-modified (::last-modified state)
        new-last-modified @*change-counter]
    (if (= old-last-modified new-last-modified)
      (::value state)
      (try
        (let [resource (resolve-resource (::resource state))]
          (::value (reset! *state {::resource resource
                                   ::value (load-resource resource)
                                   ::last-modified new-last-modified})))
        (catch Exception e
          (log/warn "Failed to load resource:" (pr-str (::resource state)))
          (if-some [value (::value state)]
            value ; file was deleted (temporarily), reuse the old value
            (throw e)))))))

(defn auto-refresher [resource load-resource]
  (let [*state (init-state resource)]
    (fn []
      (auto-refresh! *state load-resource))))
