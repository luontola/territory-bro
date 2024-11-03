(ns territory-bro.ui.sudo-page
  (:require [ring.util.http-response :as http-response]
            [territory-bro.domain.dmz :as dmz]))

(defn sudo [request]
  (-> (http-response/see-other "/")
      (assoc :session (dmz/sudo (:session request)))))

(def routes
  ["/sudo"
   {:get {:handler sudo}}])
