;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui
  (:require [hiccup2.core :as h]
            [reitit.ring :as ring]
            [ring.middleware.http-response :refer [wrap-http-response]]
            [ring.util.http-response :refer :all]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.ui.error-page :as error-page]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.open-share-page :as open-share-page]
            [territory-bro.ui.territory-page :as territory-page]))

(defn wrap-json-api-compat [handler]
  (fn [request]
    ;; TODO: update all request handlers to use :path-params instead of :params
    ;; TODO: add type coercion for :path-params
    (let [request (update request :params merge (:path-params request))]
      (handler request))))

(def ring-handler
  (ring/ring-handler
   (ring/router
    [""
     {:middleware [[html/wrap-page-path nil]
                   auth0/wrap-redirect-to-login
                   wrap-http-response
                   wrap-json-api-compat]}

     ["/"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "home page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]
     ["/join"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "join page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]
     ["/register"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "register page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]
     ["/support"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "support page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]

     open-share-page/routes

     auth0/routes

     ["/congregation/:congregation"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "congregation page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]

     ["/congregation/:congregation/territories"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "territories list page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]

     territory-page/routes

     ["/congregation/:congregation/printouts"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "printouts page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]

     ["/congregation/:congregation/settings"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "settings page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]

     ["/congregation/:congregation/support"
      {:get {:handler (fn [request]
                        (-> (h/html [:h1 "support page placeholder"])
                            (layout/page! request)
                            (html/response)))}}]

     error-page/routes])))
