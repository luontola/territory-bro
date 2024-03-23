;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.json :as json]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout])
  (:import (net.greypanther.natsort CaseInsensitiveSimpleNaturalComparator)))

(defn model! [request]
  (let [congregation (:body (api/get-congregation request))]
    {:congregation-boundaries (->> (:congregationBoundaries congregation)
                                   (map :location))
     :territories (:territories congregation)
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

(defn view [{:keys [congregation-boundaries territories permissions]}]
  (let [styles (:TerritoryListPage (css/modules))]
    (h/html
     [:h1 (i18n/t "TerritoryListPage.title")]
     (when-not (:viewCongregation permissions)
       (limited-visibility-help))

     [:div {:class (:map styles)}
      [:territory-list-map {:map-raster "osmhd"}
       [:template.json-data
        (json/write-value-as-string
         {:congregationBoundaries congregation-boundaries
          :territories (map #(select-keys % [:id :number :location :loaned :staleness])
                            territories)})]]]

     [:form.pure-form {:class (:search styles)}
      [:label {:for "territory-search"}
       (i18n/t "TerritoryListPage.search")]
      [:input#territory-search.pure-input-rounded {:type "text"
                                                   :oninput "onTerritorySearch()"
                                                   :autocomplete "off"}]
      [:button#clear-territory-search.pure-button {:type "button"
                                                   :onclick "onClearTerritorySearch()"
                                                   ;; onTerritorySearch will make this visible
                                                   :style {:display "none"}}
       (i18n/t "TerritoryListPage.clear")]]

     [:table#territory-list.pure-table.pure-table-striped
      [:thead
       [:tr
        [:th (i18n/t "Territory.number")]
        [:th (i18n/t "Territory.region")]
        [:th (i18n/t "Territory.addresses")]]]
      [:tbody
       (for [territory (sort-by (comp str :number)
                                (CaseInsensitiveSimpleNaturalComparator/getInstance)
                                territories)]
         [:tr {:data-searchable (-> (str/join "\n" [(:number territory)
                                                    (:region territory)
                                                    (:addresses territory)])
                                    (str/lower-case)
                                    (str/trim))}
          [:td {:class (:number styles)}
           [:a {:href (str html/*page-path* "/" (:id territory))}
            (if (str/blank? (:number territory))
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
