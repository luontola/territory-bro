;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.status-page
  (:require [ring.util.http-response :as http-response]))

(defn build-info []
  {:version (System/getenv "RELEASE_VERSION")
   :commit (System/getenv "GIT_COMMIT")
   :timestamp (System/getenv "BUILD_TIMESTAMP")})

(def routes
  ["/status"
   {:get {:handler (fn [_request]
                     (http-response/ok {:build (build-info)}))}}])
