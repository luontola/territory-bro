; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dev-middleware
  (:require [prone.middleware :refer [wrap-exceptions]]
            [ring-debug-logging.core :refer [wrap-with-logger]]
            [ring.middleware.reload :refer [wrap-reload]]
            [selmer.middleware :refer [wrap-error-page]]))

(defn wrap-dev [handler]
  (-> handler
      ;; FIXME: wrap-with-logger will read the body destructively so that the handler cannot anymore read it
      #_wrap-with-logger
      wrap-reload
      wrap-error-page
      ;; FIXME: prone fails to load its own css, so the error pages are useless
      #_(wrap-exceptions {:app-namespaces ['territory-bro]})))
