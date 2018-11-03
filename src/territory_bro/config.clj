; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.config
  (:require [cprop.core :as cprop]
            [cprop.source :as source]
            [cprop.tools :refer [merge-maps]]
            [mount.core :as mount]))

(defn override-defaults
  ([defaults overrides & more]
   (reduce override-defaults defaults (conj (seq more) overrides)))
  ([defaults overrides]
   (merge-maps defaults (select-keys overrides (keys defaults)))))

(mount/defstate env
  :start
  (override-defaults (cprop/load-config :resource "config-defaults.edn"
                                        :merge [(source/from-resource "config.edn")])
                     (mount/args)
                     (source/from-system-props)
                     (source/from-env)))

(defn envx [key]
  (let [value (env key)]
    (if (nil? value)
      (throw (ex-info (str key " is not configured") {:key key}))
      value)))
