;; Copyright © 2015-2023 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.router
  (:require [compojure.core :refer [routes wrap-routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [ring.middleware.http-response :as http-response]
            [territory-bro.api :as api]
            [territory-bro.infra.middleware :as middleware]))

(mount/defstate app
  :start
  (middleware/wrap-base
   (routes
    (-> #'api/api-routes
        (wrap-routes http-response/wrap-http-response)
        (wrap-routes middleware/wrap-formats))
    (route/not-found "Not Found"))))
