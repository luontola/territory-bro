;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.congregation-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.config :as config]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout]))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        congregation (dmz/get-congregation cong-id)]
    {:congregation (select-keys congregation [:congregation/name])
     :permissions {:view-printouts-page (dmz/view-printouts-page? cong-id)
                   :view-settings-page (dmz/view-settings-page? cong-id)
                   :gis-access (dmz/allowed? [:gis-access cong-id])}}))

(defn getting-started [{:keys [permissions]}]
  (let [styles (:CongregationPage (css/modules))]
    (when (:gis-access permissions)
      (h/html
       [:aside {:class (:getting-started styles)}
        (info-box/view
         {:title "Getting started checklist"} ; TODO: i18n
         (h/html
          [:ul {:class (:checklist styles)}
           [:li {:class (:completed styles)}
            [:a {:href "/documentation#how-to-create-congregation-boundaries"}
             "Define the congregation boundary"]] ; TODO: i18n
           [:li
            [:a {:href "/documentation#how-to-create-and-edit-territories"}
             "Create some territories"]]] ; TODO: i18n
          [:p (-> (i18n/t "SupportPage.mailingListAd")
                  (str/replace "<0>" "<a href=\"https://groups.google.com/g/territory-bro-announcements\" target=\"_blank\">")
                  (str/replace "</0>" "</a>")
                  (h/raw))]))]))))

(defn view [{:keys [congregation permissions] :as model}]
  (h/html
   [:h1 (:congregation/name congregation)]
   (when (:dev config/env) ; TODO: remove feature toggle
     (getting-started model))
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
