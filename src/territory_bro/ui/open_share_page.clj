;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.open-share-page
  (:require [ring.util.http-response :as http-response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.middleware :as middleware]))

(defn open-share! [request]
  (let [share-key (get-in request [:path-params :share-key])
        [share session] (dmz/open-share! share-key (:session request))]
    (when-not (some? share)
      (http-response/not-found! "Share not found"))
    (cond-> (http-response/see-other (str "/congregation/" (:congregation/id share) "/territories/" (:territory/id share)))
      ;; demo shares don't update the session
      (some? session) (assoc :session session
                             ;; Since share URLs are entrypoints to the app, we must use GET instead of POST,
                             ;; but recording that the share was opened makes this a mutative operation.
                             ::middleware/mutative-operation? true))))

(def routes
  [["/share/:share-key"
    {:get {:middleware [dmz/wrap-db-connection]
           :handler open-share!}}]
   ["/share/:share-key/*number"
    {:get {:middleware [dmz/wrap-db-connection]
           :handler open-share!}}]])
