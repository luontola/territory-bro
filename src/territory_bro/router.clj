;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.router
  (:require [compojure.core :refer [routes wrap-routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [territory-bro.api :refer [api-routes]]
            [territory-bro.middleware :as middleware]))

(mount/defstate app
  :start
  (middleware/wrap-base
    (routes
      (-> #'api-routes
          (wrap-routes middleware/wrap-formats))
      (route/not-found "Not Found"))))
