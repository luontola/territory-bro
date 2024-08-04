;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.sudo-page
  (:require [ring.util.http-response :as http-response]
            [territory-bro.domain.dmz :as dmz]))

(defn sudo [request]
  (-> (http-response/see-other "/")
      (assoc :session (dmz/sudo (:session request)))))

(def routes
  ["/sudo"
   {:get {:handler sudo}}])
