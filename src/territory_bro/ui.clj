;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui
  (:require [reitit.ring :as ring]
            [ring.middleware.http-response :refer [wrap-http-response]]
            [ring.util.http-response :as http-response]
            [territory-bro.api :as api]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.ui.congregation-page :as congregation-page]
            [territory-bro.ui.error-page :as error-page]
            [territory-bro.ui.home-page :as home-page]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.join-page :as join-page]
            [territory-bro.ui.open-share-page :as open-share-page]
            [territory-bro.ui.printouts-page :as printouts-page]
            [territory-bro.ui.registration-page :as registration-page]
            [territory-bro.ui.settings-page :as settings-page]
            [territory-bro.ui.status-page :as status-page]
            [territory-bro.ui.sudo-page :as sudo-page]
            [territory-bro.ui.support-page :as support-page]
            [territory-bro.ui.territory-list-page :as territory-list-page]
            [territory-bro.ui.territory-page :as territory-page]))

(defn wrap-json-api-compat [handler]
  (fn [request]
    ;; TODO: update all request handlers to use :path-params instead of :params
    ;; TODO: add type coercion for :path-params
    (let [request (update request :params merge (:path-params request))]
      (handler request))))

(defn wrap-current-user [handler]
  (fn [request]
    (auth/with-user-from-session request
      (handler request))))

(defn wrap-current-state [handler]
  (fn [request]
    (let [state (api/state-for-request request) ; TODO: move to DMZ? depends on session state (sudo & opened shares)
          request (assoc request :state state)]
      (handler request))))

(def routes
  [""
   {:middleware [[html/wrap-page-path nil] ; outermost middleware first
                 auth0/wrap-redirect-to-login
                 i18n/wrap-current-language
                 wrap-current-user
                 wrap-current-state
                 wrap-http-response
                 wrap-json-api-compat]}
   auth0/routes
   congregation-page/routes
   error-page/routes
   home-page/routes
   join-page/routes
   open-share-page/routes
   printouts-page/routes
   registration-page/routes
   settings-page/routes
   status-page/routes
   sudo-page/routes
   support-page/routes
   territory-list-page/routes ; must be before territory-page to avoid route conflicts
   territory-page/routes])

(def router (ring/router routes))

(def ring-handler (ring/ring-handler router (constantly (http-response/not-found "Not found"))))
