(ns territory-bro.ui.open-share-page
  (:require [clj-http.util :refer [url-encode]]
            [ring.util.http-response :as http-response]
            [ring.util.http-status :as http-status]
            [ring.util.response :as response]
            [territory-bro.domain.demo :as demo]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn share-not-found-response [request]
  (-> (h/html
       [:h1 {} (i18n/t "OpenSharePage.notFound.title")]
       [:p {} (i18n/t "OpenSharePage.notFound.description")])
      (layout/page! request)
      (html/response)
      (response/status http-status/forbidden)))

(defn open-share! [request]
  (let [share-key (get-in request [:path-params :share-key])
        [share session] (dmz/open-share! share-key (:session request))]
    (if (nil? share)
      (share-not-found-response request)
      (cond-> (http-response/see-other (str "/congregation/" (:congregation/id share) "/territories/" (:territory/id share)
                                            (when-not (demo/demo-id? (:congregation/id share))
                                              (str "?share-key=" (url-encode share-key)))))
        ;; demo shares don't update the session
        (some? session) (assoc :session session
                               ;; Since share URLs are entrypoints to the app, we must use GET instead of POST,
                               ;; but recording that the share was opened makes this a mutative operation.
                               ::middleware/mutative-operation? true)))))

(def routes
  ["/share/:share-key"
   {:middleware [dmz/wrap-db-connection]}
   [""
    {:get {:handler open-share!}}]
   ["/*number"
    {:get {:handler open-share!}}]])
