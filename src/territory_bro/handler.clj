; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.handler
  (:require [compojure.core :refer [defroutes routes wrap-routes]]
            [compojure.route :as route]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [territory-bro.db.core :as db]
            [territory-bro.layout :refer [error-page]]
            [territory-bro.middleware :as middleware]
            [territory-bro.routes :refer [home-routes]]))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []

  (timbre/merge-config!
   {:level (if (env :dev) :trace :info)
    :appenders {:rotor (rotor/rotor-appender
                        {:path "territory_bro.log"
                         :max-size (* 512 1024)
                         :backlog 10})}})

  (db/connect!)
  (timbre/info (str "\n-=[territory-bro started successfully"
                    (when (env :dev) " using the development profile")
                    "]=-")))

(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (timbre/info "territory-bro is shutting down...")
  (db/disconnect!)
  (timbre/info "shutdown complete!"))

(def app-routes
  (routes
   ; TODO: CSRF token for API
   #_(wrap-routes #'home-routes middleware/wrap-csrf)
   #'home-routes
   (route/not-found
    (:body
     (error-page {:status 404
                  :title "page not found"})))))

(def app (middleware/wrap-base #'app-routes))
