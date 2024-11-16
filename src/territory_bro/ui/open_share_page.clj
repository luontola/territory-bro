(ns territory-bro.ui.open-share-page
  (:require [clj-http.util :refer [url-encode]]
            [ring.util.http-response :as http-response]
            [territory-bro.domain.demo :as demo]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.middleware :as middleware]))

(defn open-share! [request]
  (let [share-key (get-in request [:path-params :share-key])
        [share session] (dmz/open-share! share-key (:session request))]
    (when-not (some? share)
      (http-response/not-found! "Share not found"))
    (cond-> (http-response/see-other (str "/congregation/" (:congregation/id share) "/territories/" (:territory/id share)
                                          (when-not (demo/demo-id? (:congregation/id share))
                                            (str "?share-key=" (url-encode share-key)))))
      ;; demo shares don't update the session
      (some? session) (assoc :session session
                             ;; Since share URLs are entrypoints to the app, we must use GET instead of POST,
                             ;; but recording that the share was opened makes this a mutative operation.
                             ::middleware/mutative-operation? true))))

(def routes
  ["/share/:share-key"
   {:middleware [dmz/wrap-db-connection]}
   [""
    {:get {:handler open-share!}}]
   ["/*number"
    {:get {:handler open-share!}}]])
