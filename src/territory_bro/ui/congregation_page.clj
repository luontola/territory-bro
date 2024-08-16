;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.congregation-page
  (:require [hiccup2.core :as h]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        congregation (dmz/get-congregation cong-id)]
    {:congregation (select-keys congregation [:congregation/name])
     :permissions {:view-printouts-page (dmz/view-printouts-page? cong-id)
                   :view-settings-page (dmz/view-settings-page? cong-id)}}))

(defn view [{:keys [congregation permissions]}]
  (h/html
   [:h1 (:congregation/name congregation)]
   [:p [:a {:href (str html/*page-path* "/territories")}
        (i18n/t "TerritoryListPage.title")]]
   (when (:view-printouts-page permissions)
     [:p [:a {:href (str html/*page-path* "/printouts")}
          (i18n/t "PrintoutPage.title")]])
   (when (:view-settings-page permissions)
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
