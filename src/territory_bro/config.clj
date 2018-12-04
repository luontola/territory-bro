;; Copyright © 2015-2018 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.config
  (:require [cprop.core :as cprop]
            [cprop.source :as source]
            [cprop.tools :refer [merge-maps]]
            [mount.core :as mount]
            [territory-bro.util :refer [getx]]
            [clojure.string :as str])
  (:import (java.time Instant)))

(defn override-defaults
  ([defaults overrides & more]
   (reduce override-defaults defaults (conj (seq more) overrides)))
  ([defaults overrides]
   (merge-maps defaults (select-keys overrides (keys defaults)))))

(defn whitespace-separated-list [s]
  (remove empty? (str/split (str s) #"\s+")))

(defn- enrich-tenant [tenant]
  (assoc tenant :admins (into #{} (whitespace-separated-list (:admins tenant)))))

(defn- enrich-tenants [tenants]
  (->> tenants
       (map (fn [[k v]]
              [k (enrich-tenant v)]))
       (into {})))

(defn enrich-env [env]
  (assoc env :now #(Instant/now)
             :jwt-issuer (str "https://" (getx env :auth0-domain) "/")
             :jwt-audience (getx env :auth0-client-id)
             :tenant (enrich-tenants (:tenant env))))

(mount/defstate ^:dynamic env
  :start
  (enrich-env
    (override-defaults (cprop/load-config :resource "config-defaults.edn"
                                          :merge [(source/from-resource "config.edn")])
                       (mount/args)
                       (source/from-system-props)
                       (source/from-env))))
