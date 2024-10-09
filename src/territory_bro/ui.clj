;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui
  (:require [reitit.ring :as ring]
            [ring.util.http-response :as http-response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.ui.congregation-page :as congregation-page]
            [territory-bro.ui.documentation-page :as documentation-page]
            [territory-bro.ui.error-page :as error-page]
            [territory-bro.ui.home-page :as home-page]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.join-page :as join-page]
            [territory-bro.ui.open-share-page :as open-share-page]
            [territory-bro.ui.printouts-page :as printouts-page]
            [territory-bro.ui.privacy-policy-page :as privacy-policy-page]
            [territory-bro.ui.registration-page :as registration-page]
            [territory-bro.ui.settings-page :as settings-page]
            [territory-bro.ui.status-page :as status-page]
            [territory-bro.ui.sudo-page :as sudo-page]
            [territory-bro.ui.support-page :as support-page]
            [territory-bro.ui.territory-list-page :as territory-list-page]
            [territory-bro.ui.territory-page :as territory-page]))

(defn- parse-mandatory-uuid [s]
  (or (parse-uuid s)
      (throw (http-response/not-found! "Not found"))))

(defn- parse-congregation-id [s]
  (if (= "demo" s)
    "demo"
    (parse-mandatory-uuid s)))

(defn wrap-parse-path-params [handler]
  (fn [request]
    (let [{:keys [congregation territory]} (:path-params request)
          request (cond-> request
                    (some? congregation) (update-in [:path-params :congregation] parse-congregation-id)
                    (some? territory) (update-in [:path-params :territory] parse-mandatory-uuid))]
      (handler request))))

(def routes
  [""
   {:middleware [[html/wrap-page-path nil] ; outermost middleware first
                 dmz/wrap-current-state
                 wrap-parse-path-params]}
   auth0/routes
   congregation-page/routes
   documentation-page/routes
   error-page/routes
   home-page/routes
   join-page/routes
   open-share-page/routes
   printouts-page/routes
   privacy-policy-page/routes
   registration-page/routes
   settings-page/routes
   status-page/routes
   sudo-page/routes
   support-page/routes
   territory-list-page/routes ; must be before territory-page to avoid route conflicts
   territory-page/routes])

(def router (ring/router routes))

(def ring-handler (ring/ring-handler router (constantly (http-response/not-found "Not found"))))
