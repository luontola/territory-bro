;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui
  (:require [hiccup2.core :as h]
            [reitit.ring :as ring]
            [ring.util.http-response :refer :all]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.layout :as layout]
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
                   wrap-json-api-compat]}

     ["/"
      {:get {:handler (fn [request]
                        (let [title "home page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]
     ["/join"
      {:get {:handler (fn [request]
                        (let [title "join page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]
     ["/register"
      {:get {:handler (fn [request]
                        (let [title "register page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]
     ["/support"
      {:get {:handler (fn [request]
                        (let [title "support page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]
     ["/share/:share-key"
      {:get {:handler (fn [request]
                        (let [title "share page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title]
                                                   [:p (-> request :path-params :share-key)])))))}}]
     ["/share/:share-key/*number"
      {:get {:handler (fn [request]
                        (let [title "share page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title]
                                                   [:p (-> request :path-params :share-key)]
                                                   [:p (-> request :path-params :number)])))))}}]

     auth0/routes

     ["/congregation/:congregation"
      {:get {:handler (fn [request]
                        (let [title "congregation page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]

     ["/congregation/:congregation/territories"
      {:get {:handler (fn [request]
                        (let [title "territories list page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]

     territory-page/routes

     ["/congregation/:congregation/printouts"
      {:get {:handler (fn [request]
                        (let [title "printouts page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]

     ["/congregation/:congregation/settings"
      {:get {:handler (fn [request]
                        (let [title "settings page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]

     ["/congregation/:congregation/support"
      {:get {:handler (fn [request]
                        (let [title "support page placeholder"]
                          (html/response (layout/page! request {:title title}
                                           (h/html [:h1 title])))))}}]])))
