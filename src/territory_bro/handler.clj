; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [territory-bro.config :refer [env]]
            [territory-bro.env :refer [defaults]]
            [territory-bro.layout :refer [error-page]]
            [territory-bro.middleware :as middleware]
            [territory-bro.routes :refer [home-routes]]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop ((or (:stop defaults) identity)))

(mount/defstate app
  :start
  (middleware/wrap-base
   (routes
    (-> #'home-routes
        ;; TODO: CSRF token for API
        #_(wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (route/not-found
     (:body
      (error-page {:status 404
                   :title "page not found"}))))))
