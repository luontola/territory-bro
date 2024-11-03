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
