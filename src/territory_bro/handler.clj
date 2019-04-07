;; Copyright © 2015-2018 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [territory-bro.env :refer [defaults]]
            [territory-bro.middleware :as middleware]
            [territory-bro.routes :refer [api-routes]]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop ((or (:stop defaults) identity)))

(mount/defstate app
  :start
  (middleware/wrap-base
    (routes
      (-> #'api-routes
          (wrap-routes middleware/wrap-formats))
      (route/not-found "Not Found"))))
