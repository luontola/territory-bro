;; Copyright © 2015-2018 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dev-middleware
  (:require [ring-debug-logging.core :refer [wrap-with-logger]]
            [ring.middleware.reload :refer [wrap-reload]]))

(defn wrap-dev [handler]
  (-> handler
      ;; FIXME: wrap-with-logger will read the body destructively so that the handler cannot anymore read it
      #_wrap-with-logger
      (wrap-reload {:dirs ["src" "resources"]})))
