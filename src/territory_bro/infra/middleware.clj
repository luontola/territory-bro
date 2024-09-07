;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.middleware
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring-ttl-session.core :as ttl-session]
            [ring.logger :as logger]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.http-response :refer [wrap-http-response]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.resource :as resource]
            [ring.util.http-response :refer [internal-server-error]]
            [ring.util.response :as response]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :refer [env]]
            [territory-bro.infra.util :as util]
            [territory-bro.projections :as projections]
            [territory-bro.ui.error-page :as error-page]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n])
  (:import (java.time Duration)))

(defn wrap-internal-error [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
        (log/error t (str "Uncaught exception "
                          (pr-str (select-keys request [:request-method :uri]))
                          "\n"
                          (pr-str t)))
        (-> (internal-server-error "Internal Server Error")
            (response/content-type "text/html; charset=utf-8"))))))

(defn wrap-sqlexception-chain [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (throw (util/fix-sqlexception-chain t))))))

(defn wrap-formats [handler]
  (wrap-restful-format handler {:formats [:json-kw]}))

(defn wrap-default-content-type [handler]
  ;; Chrome gives an ERR_INVALID_RESPONSE error about 4xx pages
  ;; with application/octet-stream content type, which is Ring's
  ;; default when no content type is not defined.
  (fn [request]
    (let [response (handler request)]
      (if (response/get-header response "Content-Type")
        response
        (response/content-type response "text/plain")))))

;; defonce to avoid forgetting sessions every time the code is reloaded in development mode
(defonce session-store (ttl-session/ttl-memory-store (.toSeconds (Duration/ofHours 4))))


(defn- refresh-projections! []
  (projections/refresh-async!)
  ;; TODO: store the observed revision in session and await before the next read if the cache is out of date
  (projections/await-refreshed (Duration/ofSeconds 10)))

(def ^:private mutative-request-methods #{:post :put :delete :patch})

(defn wrap-auto-refresh-projections [handler]
  (fn [request]
    (let [response (handler request)]
      (when (or (contains? mutative-request-methods (:request-method request))
                (::mutative-operation? response))
        (log/info "Refreshing projections in response to" (:request-method request) (:uri request))
        (refresh-projections!))
      response)))


(defn- find-compressed-resource [request root-path]
  (when-some [uncompressed (resource/resource-request request root-path)]
    (let [accept-encoding (str (get-in request [:headers "accept-encoding"]))
          accept-encoding? (set (str/split accept-encoding #"[^a-z]+"))
          compressed-resource (fn [encoding suffix]
                                (when (accept-encoding? encoding)
                                  (some-> (resource/resource-request (update request :uri #(str % suffix)) root-path)
                                          (response/header "Content-Encoding" encoding))))
          content-length #(parse-long (or (response/get-header % "Content-Length") "0"))]
      (->> [(compressed-resource "br" ".br")
            (compressed-resource "gzip" ".gz")
            uncompressed]
           (filterv some?)
           (sort-by content-length) ; the candidates should already be smallest first, but in rare cases brotli might not be the smallest
           (first)))))

(defn wrap-compressed-resources [handler root-path]
  (fn [request]
    (or (find-compressed-resource request root-path)
        (handler request))))


(defn- static-asset? [path]
  (or (str/starts-with? path "/assets/")
      (= "/favicon.ico" path)))

(defn- content-hashed? [path]
  (some? (re-find html/content-hashed-filename path)))

(defn wrap-cache-control [handler]
  (fn [request]
    (let [response (handler request)
          path (:uri request)]
      (if (some? (response/get-header response "cache-control"))
        response
        (response/header response "Cache-Control"
                         (if (and (= 200 (:status response))
                                  (static-asset? path))
                           (if (content-hashed? path)
                             "public, max-age=31536000, immutable" ; max-age=365d
                             "public, max-age=3600, stale-while-revalidate=86400") ; max-age=1h, stale-while-revalidate=24h
                           "private, no-cache"))))))

(defn wrap-base [handler]
  (-> handler
      wrap-auto-refresh-projections
      (cond->
        (:dev env) (wrap-reload {:dirs ["src" "resources"]}))
      wrap-sqlexception-chain
      wrap-http-response
      wrap-formats
      wrap-default-content-type
      auth0/wrap-redirect-to-login
      error-page/wrap-error-pages
      i18n/wrap-current-language
      auth/wrap-current-user
      (logger/wrap-with-logger {:request-keys (conj logger/default-request-keys :remote-addr)})
      (wrap-compressed-resources "/public") ; XXX: this would belong between the middleware stack of wrap-defaults (no cookies), consider inlining wrap-defaults or moving middleware to reitit
      (wrap-defaults (-> site-defaults
                         (assoc :proxy true)
                         (dissoc :static)
                         (assoc-in [:session :store] session-store)
                         (assoc-in [:session :flash] false)))
      wrap-cache-control
      wrap-internal-error))
