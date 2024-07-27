;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.congregation-page
  (:require [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.visible :as visible]))

(defn model! [request]
  (let [demo? (= "demo" (get-in request [:path-params :congregation]))
        congregation (if demo?
                       (:body (api/get-demo-congregation request))
                       (:body (api/get-congregation request {})))]
    (select-keys congregation [:congregation/name :congregation/permissions])))

(defn view [model]
  (h/html
   [:h1 (:congregation/name model)]
   [:p [:a {:href (str html/*page-path* "/territories")}
        (i18n/t "TerritoryListPage.title")]]
   (when (visible/printouts-page? (:congregation/permissions model))
     [:p [:a {:href (str html/*page-path* "/printouts")}
          (i18n/t "PrintoutPage.title")]])
   (when (visible/settings-page? (:congregation/permissions model))
     [:p [:a {:href (str html/*page-path* "/settings")}
          (i18n/t "SettingsPage.title")]])))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/congregation/:congregation"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}}])
