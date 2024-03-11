;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout]))

(defn model! [request]
  (let [congregation (:body (api/get-congregation request))]
    {:territories (->> (:territories congregation)
                       ;;  TODO: use natural sort
                       (sort-by :number))
     :permissions (:permissions congregation)}))

(defn limited-visibility-help []
  (info-box/view
   {:title (i18n/t "TerritoryListPage.limitedVisibility.title")}
   (h/html
    [:p
     (i18n/t "TerritoryListPage.limitedVisibility.explanation")
     " "
     (if (auth/logged-in?)
       (-> (i18n/t "TerritoryListPage.limitedVisibility.needToRequestAccess")
           (str/replace "<0>" "<a href=\"/join\">")
           (str/replace "</0>" "</a>")
           (h/raw))
       (-> (i18n/t "TerritoryListPage.limitedVisibility.needToLogin")
           (str/replace "<0>" "<a href=\"/login\">")
           (str/replace "</0>" "</a>")
           (h/raw)))])))

(defn view [{:keys [territories permissions]}]
  (let [styles (:TerritoryListPage (css/modules))]
    (h/html
     [:h1 (i18n/t "TerritoryListPage.title")]
     (when-not (:viewCongregation permissions)
       (limited-visibility-help))
     ;; TODO: this is an MVP - migrate the rest of the page
     [:table#territory-list.pure-table.pure-table-striped
      [:thead
       [:tr
        [:th (i18n/t "Territory.number")]
        [:th (i18n/t "Territory.region")]
        [:th (i18n/t "Territory.addresses")]]]
      [:tbody
       (for [territory territories]
         [:tr
          [:td {:class (:number styles)}
           [:a {:href (str html/*page-path* "/" (:id territory))}
            (if (str/blank? (:number territory)) ; TODO: test "-"
              "-"
              (:number territory))]]
          [:td (:region territory)]
          [:td (:addresses territory)]])]])))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/congregation/:congregation/territories"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}}])
