; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.jwt
  (:require [clojure.data.json :as json]
            [mount.core :as mount]
            [territory-bro.config :refer [envx]])
  (:import (com.auth0.jwk JwkProviderBuilder JwkProvider)
           (com.auth0.jwt JWT)
           (com.auth0.jwt.algorithms Algorithm)
           (com.auth0.jwt.interfaces DecodedJWT)
           (java.nio.charset StandardCharsets)
           (java.time Instant)
           (java.util Base64)))

(mount/defstate ^:dynamic ^JwkProvider jwk-provider
  :start (-> (JwkProviderBuilder. ^String (envx :auth0-domain))
             (.build)))

(defn- fetch-public-key [^DecodedJWT jwt]
  (.getPublicKey (.get jwk-provider (.getKeyId jwt))))

(defn- decode-base64url [^String base64-str]
  (-> (Base64/getUrlDecoder)
      (.decode base64-str)
      (String. StandardCharsets/UTF_8)))

(defn validate [jwt-str]
  (let [jwt (JWT/decode jwt-str)
        pubkey (fetch-public-key jwt)]
    ;; verify signature
    (-> (Algorithm/RSA256 pubkey nil)
        (.verify jwt))
    ;; TODO: validate token expiration (extract the code from t-b.routes/login)
    ;; TODO: validate token issuer
    ;; TODO: validate token audience
    ; https://auth0.com/docs/tokens/id-token#validate-an-id-token
    (-> (.getPayload jwt)
        (decode-base64url)
        (json/read-str :key-fn keyword))))

(defn expired?
  ([jwt]
   (expired? jwt (Instant/now)))
  ([jwt ^Instant now]
   (< (:exp jwt) (.getEpochSecond now))))
