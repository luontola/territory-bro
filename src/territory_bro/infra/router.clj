;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.router
  (:require [compojure.core :refer [routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [territory-bro.api :as api]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui :as ui]))

(mount/defstate app
  :start
  (-> (routes #'api/api-routes
              #'ui/ring-handler
              (route/not-found "Not Found"))
      (middleware/wrap-base)))
