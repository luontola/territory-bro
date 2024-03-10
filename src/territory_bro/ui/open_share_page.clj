;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.open-share-page
  (:require [ring.util.http-response :as http-response]
            [territory-bro.api :as api]))

;; TODO: migrate tests territory-bro.api-test/share-territory-link-test & share-demo-territory-link-test to call this function
(defn open-share! [request]
  (let [{:keys [status body session]} (api/open-share request)]
    (when-not (= 200 status)
      (http-response/not-found! "Share not found"))
    (cond-> (http-response/see-other (str "/congregation/" (:congregation body) "/territories/" (:territory body)))
      (some? session) (assoc :session session))))

(def routes
  [["/share/:share-key"
    {:get {:handler open-share!}}]
   ["/share/:share-key/*number"
    {:get {:handler open-share!}}]])
