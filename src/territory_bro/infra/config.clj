(ns territory-bro.infra.config
  (:require [clojure.string :as str]
            [cprop.core :as cprop]
            [mount.core :as mount]
            [schema.core :as s]
            [territory-bro.infra.util :as util]
            [territory-bro.infra.util :refer [getx]])
  (:import (java.time Clock Instant)))

(def ^:dynamic ^Clock *clock* (Clock/systemUTC))

(defn now ^Instant []
  (Instant/now *clock*))

(s/set-max-value-length! 50)

(def ^:private base-url-pattern #"https?://.+[^/]")
(def BaseUrl (s/conditional string? (s/pred #(re-matches base-url-pattern %)
                                            (symbol (str "re-matches \"" base-url-pattern "\"")))))

(s/defschema Env
  {:auth0-client-id s/Str
   :auth0-client-secret s/Str
   :auth0-domain s/Str
   :public-url BaseUrl
   :qr-code-base-url BaseUrl
   :database-schema s/Str
   :database-url s/Str
   :demo-congregation (s/maybe s/Uuid)
   :dev s/Bool
   :gis-database-host s/Str
   :gis-database-name s/Str
   :jwt-audience s/Str
   :jwt-issuer s/Str
   :port s/Int
   :super-users #{(s/cond-pre s/Str s/Uuid)}
   :support-email s/Str})

(def validate-env (s/validator Env))

(defn enrich-env [env]
  (assoc env
         :jwt-issuer (str "https://" (getx env :auth0-domain) "/")
         :jwt-audience (getx env :auth0-client-id)
         :super-users (->> (str/split (or (:super-users env) "")
                                      #"\s+")
                           (remove str/blank?)
                           (map util/parse-uuid-or-string)
                           (set))
         :demo-congregation (some-> (:demo-congregation env)
                                    parse-uuid)))

(defn load-config []
  (cprop/load-config :resource "config-defaults.edn")) ; TODO: use ":as-is? true" and schema coercion?)

(mount/defstate ^:dynamic env
  :start (-> (load-config)
             (enrich-env)
             (validate-env)))
