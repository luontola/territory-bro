;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.config
  (:require [clojure.string :as str]
            [cprop.core :as cprop]
            [cprop.source :as source]
            [cprop.tools :refer [merge-maps]]
            [mount.core :as mount]
            [territory-bro.util :refer [getx]])
  (:import (java.time Instant)
           (java.util UUID)))

(defn override-defaults
  ([defaults overrides & more]
   (reduce override-defaults defaults (conj (seq more) overrides)))
  ([defaults overrides]
   (merge-maps defaults (select-keys overrides (keys defaults)))))

(defn try-parse-uuid [s]
  (try
    (UUID/fromString s)
    (catch Exception _
      s)))

(defn enrich-env [env]
  (assoc env
         :now #(Instant/now)
         :jwt-issuer (str "https://" (getx env :auth0-domain) "/")
         :jwt-audience (getx env :auth0-client-id)
         :super-users (->> (str/split (or (:super-users env) "")
                                      #"\s+")
                           (remove str/blank?)
                           (map try-parse-uuid)
                           (set))))

(mount/defstate ^:dynamic env
  :start (-> (override-defaults
              (cprop/load-config :resource "config-defaults.edn")
              (source/from-system-props {:as-is? false})
              (source/from-env {:as-is? false}))
             (enrich-env)
             (merge (mount/args))))
