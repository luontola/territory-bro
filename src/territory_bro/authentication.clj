; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.authentication
  (:require [clojure.data.json :as json]
            [mount.core :as mount]
            [territory-bro.config :refer [env envx]])
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

(defn decode-jwt [jwt-str]
  (let [jwt (JWT/decode jwt-str)
        pubkey (fetch-public-key jwt)]
    (-> (Algorithm/RSA256 pubkey nil)
        (.verify jwt))
    (-> (.getPayload jwt)
        (decode-base64url)
        (json/read-str :key-fn keyword))))

(defn jwt-expired?
  ([jwt]
   (jwt-expired? jwt (Instant/now)))
  ([jwt ^Instant now]
   (< (:exp jwt) (.getEpochSecond now))))

(def ^:dynamic *user*)

(defn save-user [session jwt]
  (assoc session :user (select-keys jwt [:sub :name :email :email_verified :picture])))

(defn with-authenticated-user* [request f]
  (binding [*user* (get-in request [:session :user])]
    (f)))

(defmacro with-authenticated-user [request & body]
  `(with-authenticated-user* ~request (fn [] ~@body)))

(defn super-admin?
  ([]
   (super-admin? *user* env))
  ([user env]
   (if-let [super-admin (env :super-admin)]
     (= (:sub user) super-admin)
     false)))

(defn- permission-to-view-tenant? [id]
  ; TODO: fine-grained authorization
  (super-admin?))

(defn authorized-tenants []
  (->> (keys (env :tenant))
       (filter permission-to-view-tenant?)
       (doall)))
