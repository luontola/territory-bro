;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.config
  (:require [clojure.string :as str]
            [cprop.core :as cprop]
            [mount.core :as mount]
            [schema.core :as s]
            [territory-bro.util :refer [getx]])
  (:import (java.time Instant)
           (java.util UUID)))

(s/defschema Env
  {:auth0-client-id s/Str
   :auth0-domain s/Str
   :database-schema s/Str
   :database-url s/Str
   :demo-congregation (s/maybe s/Uuid)
   :dev s/Bool
   :gis-database-host s/Str
   :gis-database-name s/Str
   :gis-database-ssl-mode s/Str
   :jwt-audience s/Str
   :jwt-issuer s/Str
   :now (s/pred fn?)
   :nrepl-bind s/Str
   :nrepl-port (s/maybe s/Int)
   :port s/Int
   :super-users #{(s/cond-pre s/Str s/Uuid)}
   :support-email s/Str})

(def validate-env (s/validator Env))

(defn- try-parse-uuid [s]
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
                           (set))
         :demo-congregation (try-parse-uuid (:demo-congregation env))))

(defn load-config []
  (cprop/load-config :resource "config-defaults.edn")) ; TODO: use ":as-is? true" and schema coercion?)

(mount/defstate ^:dynamic env
  :start (-> (load-config)
             (enrich-env)
             (validate-env)))
