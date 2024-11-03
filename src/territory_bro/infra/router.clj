(ns territory-bro.infra.router
  (:require [mount.core :as mount]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui :as ui]))

(mount/defstate app
  :start
  (-> #'ui/ring-handler
      (middleware/wrap-base)))
