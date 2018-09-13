; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.middleware
  (:require [environ.core :refer [env]]
            [immutant.web.middleware :refer [wrap-session]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.reload :as reload]
            [taoensso.timbre :as timbre]
            [territory-bro.layout :refer [error-page]]
            [territory-bro.util :as util]))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (timbre/error t)
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn wrap-sqlexception-chain [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (throw (util/fix-sqlexception-chain t))))))

(defn wrap-dev [handler]
  (if (env :dev)
    (-> handler
        reload/wrap-reload
        wrap-exceptions)
    handler))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
   handler
   {:error-response (error-page {:status 403
                                 :title "Invalid anti-forgery token"})}))

(defn wrap-formats [handler]
  (wrap-restful-format handler {:formats [:json-kw :transit-json :transit-msgpack]}))

(defn wrap-base [handler]
  (-> handler
      wrap-sqlexception-chain
      wrap-dev
      wrap-formats
      wrap-flash
      (wrap-session {:cookie-attrs {:http-only true}})
      (wrap-defaults
       (-> site-defaults
           (assoc-in [:security :anti-forgery] false)
           (dissoc :session)))
      wrap-internal-error))
