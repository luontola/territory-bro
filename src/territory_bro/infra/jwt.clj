(ns territory-bro.infra.jwt
  (:require [mount.core :as mount]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.config :refer [env]]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.util :as util]
            [territory-bro.infra.util :refer [getx]])
  (:import (com.auth0.jwk JwkProvider JwkProviderBuilder)
           (com.auth0.jwt JWT JWTVerifier$BaseVerification)
           (com.auth0.jwt.algorithms Algorithm)
           (java.time Instant)))

(mount/defstate ^:dynamic ^JwkProvider jwk-provider
  :start (-> (JwkProviderBuilder. ^String (getx env :auth0-domain))
             (.build)))

(defn- fetch-public-key [^String jwt]
  (let [key-id (.getKeyId (JWT/decode jwt))]
    (.getPublicKey (.get jwk-provider key-id))))

(defn- ^String/1 strings [& ss]
  (into-array String ss))

(defn validate [^String jwt env]
  (let [public-key (fetch-public-key jwt)
        algorithm (Algorithm/RSA256 public-key nil)
        verifier (-> (JWT/require algorithm)
                     (.withIssuer (strings (getx env :jwt-issuer)))
                     (.withAudience (strings (getx env :jwt-audience)))
                     (.acceptLeeway 60)
                     (->> ^JWTVerifier$BaseVerification (cast JWTVerifier$BaseVerification))
                     (.build config/*clock*))]
    (-> (.verify verifier jwt)
        (.getPayload)
        (util/decode-base64url)
        (json/read-value))))

(defn expired?
  ([jwt]
   (expired? jwt (Instant/now)))
  ([jwt ^Instant now]
   (< (:exp jwt) (.getEpochSecond now))))
