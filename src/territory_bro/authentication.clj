; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.authentication
  (:require [clojure.data.json :as json]
            [mount.core :as mount]
            [territory-bro.config :refer [envx]])
  (:import (com.auth0.jwk JwkProviderBuilder JwkProvider)
           (com.auth0.jwt JWT)
           (com.auth0.jwt.algorithms Algorithm)
           (com.auth0.jwt.interfaces DecodedJWT)
           (java.nio.charset StandardCharsets)
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

(defn decode-jwt [jwt-str]
  (let [jwt (JWT/decode jwt-str)
        pubkey (fetch-public-key jwt)]
    (-> (Algorithm/RSA256 pubkey nil)
        (.verify jwt))
    (-> (.getPayload jwt)
        (decode-base64url)
        (json/read-str :key-fn keyword))))
