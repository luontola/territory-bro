;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui
  (:require [hiccup2.core :as h]
            [reitit.core :as reitit]
            [reitit.ring :as ring]
            [ring.util.http-response :refer :all]
            [ring.util.response :as response]
            [territory-bro.api :as api]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.territory-page :as territory-page]))

(defn wrap-page-path [handler route-name]
  (fn [request]
    (let [page-path (if (some? route-name)
                      (-> (::reitit/router request)
                          (reitit/match-by-name route-name (:path-params request))
                          (reitit/match->path))
                      (:uri request))]
      (assert (some? page-path))
      (binding [html/*page-path* page-path]
        (handler request)))))

(defn wrap-json-api-compat [handler]
  (fn [request]
    ;; TODO: update all request handlers to use :path-params instead of :params
    ;; TODO: add type coercion for :path-params
    (let [request (update request :params merge (:path-params request))]
      (handler request))))

(defn html-response [html]
  (when (some? html)
    (-> (ok (str html))
        (response/content-type "text/html"))))

(def ring-handler
  (ring/ring-handler
   (ring/router
    [[""
      {:middleware [[wrap-page-path nil]
                    wrap-json-api-compat]}

      ["/"
       {:get {:handler (fn [_request]
                         (let [title "home page placeholder"]
                           (html-response (layout/page {:title title}
                                            (h/html [:h1 title])))))}}]
      ["/join"
       {:get {:handler (fn [_request]
                         (let [title "join page placeholder"]
                           (html-response (layout/page {:title title}
                                            (h/html [:h1 title])))))}}]
      ["/register"
       {:get {:handler (fn [_request]
                         (let [title "register page placeholder"]
                           (html-response (layout/page {:title title}
                                            (h/html [:h1 title])))))}}]
      ["/support"
       {:get {:handler (fn [_request]
                         (let [title "support page placeholder"]
                           (html-response (layout/page {:title title}
                                            (h/html [:h1 title])))))}}]
      ["/share/:share-key"
       {:get {:handler (fn [request]
                         (let [title "share page placeholder"]
                           (html-response (layout/page {:title title}
                                            (h/html [:h1 title]
                                                    [:p (-> request :path-params :share-key)])))))}}]
      ["/share/:share-key/*number"
       {:get {:handler (fn [request]
                         (let [title "share page placeholder"]
                           (html-response (layout/page {:title title}
                                            (h/html [:h1 title]
                                                    [:p (-> request :path-params :share-key)]
                                                    [:p (-> request :path-params :number)])))))}}]

      ["/login"
       {:get {:handler auth0/login-handler}}]
      ["/login-callback"
       {:get {:handler auth0/login-callback-handler}}]
      ["/logout"
       {:get {:handler auth0/logout-handler}}]

      ["/congregation/:congregation"
       {:get {:handler (fn [request]
                         (let [title "congregation page placeholder"
                               congregation (:body (api/get-congregation request))]
                           (html-response (layout/page {:title title
                                                        :congregation congregation}
                                            (h/html [:h1 title])))))}}]

      ["/congregation/:congregation/territories"
       {:get {:handler (fn [request]
                         (let [title "territories list page placeholder"
                               congregation (:body (api/get-congregation request))]
                           (html-response (layout/page {:title title
                                                        :congregation congregation}
                                            (h/html [:h1 title])))))}}]

      ["/congregation/:congregation/territories/:territory"
       {:middleware [[wrap-page-path ::territory-page]]}
       [""
        {:name ::territory-page
         :get {:handler (fn [request]
                          (let [congregation (:body (api/get-congregation request))]
                            (html-response (layout/page {:title "Territory Page"
                                                         :congregation congregation}
                                             (territory-page/page! request)))))}}]

       ["/do-not-calls/edit"
        {:get {:handler (fn [request]
                          (html-response (territory-page/do-not-calls--edit! request)))}}]

       ["/do-not-calls/save"
        {:post {:handler (fn [request]
                           (html-response (territory-page/do-not-calls--save! request)))}}]

       ["/share-link/open"
        {:get {:middleware [middleware/wrap-always-refresh-projections]
               :handler (fn [request]
                          (-> (html-response (territory-page/share-link--open! request))
                              ;; avoid creating lots of new shares if the user clicks the share button repeatedly
                              (response/header "Cache-Control" "max-age=300, must-revalidate")))}}]

       ["/share-link/close"
        {:get {:handler (fn [_request]
                          (html-response (territory-page/share-link--closed)))}}]]

      ["/congregation/:congregation/printouts"
       {:get {:handler (fn [request]
                         (let [title "printouts page placeholder"
                               congregation (:body (api/get-congregation request))]
                           (html-response (layout/page {:title title
                                                        :congregation congregation}
                                            (h/html [:h1 title])))))}}]

      ["/congregation/:congregation/settings"
       {:get {:handler (fn [request]
                         (let [title "settings page placeholder"
                               congregation (:body (api/get-congregation request))]
                           (html-response (layout/page {:title title
                                                        :congregation congregation}
                                            (h/html [:h1 title])))))}}]

      ["/congregation/:congregation/support"
       {:get {:handler (fn [request]
                         (let [title "support page placeholder"
                               congregation (:body (api/get-congregation request))]
                           (html-response (layout/page {:title title
                                                        :congregation congregation}
                                            (h/html [:h1 title])))))}}]]])))
