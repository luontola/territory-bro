;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [ring.util.http-response :as http-response]
            [territory-bro.api :as api]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.json :as json]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.maps :as maps])
  (:import (net.greypanther.natsort CaseInsensitiveSimpleNaturalComparator)))

(defn model! [request {:keys [fetch-loans?]}]
  (let [cong-id (get-in request [:path-params :congregation])
        congregation (dmz/get-congregation dmz/*state* cong-id)
        _ (when-not congregation
            ;; This function must support anonymous access for opened shares.
            ;; If anonymous user cannot see the congregation, first prompt them
            ;; to login before giving the forbidden error.
            (api/require-logged-in!)
            (http-response/forbidden! "No congregation access"))
        congregation-boundary (->> (:congregation/congregation-boundaries congregation)
                                   (mapv (comp geometry/parse-wkt :congregation-boundary/location))
                                   ;; TODO: precompute the union in the state - there are very few places where the boundaries are handled by ID
                                   (geometry/union)
                                   (str))
        territories (dmz/list-territories! dmz/*state* cong-id {:fetch-loans? fetch-loans?})]
    {:congregation-boundary congregation-boundary
     :territories territories
     :has-loans? (some? (:congregation/loans-csv-url congregation))
     :permissions (select-keys (:congregation/permissions congregation) [:view-congregation])}))


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


(defn territory-list-map [{:keys [congregation-boundary territories]}]
  (h/html
   [:territory-list-map {:map-raster maps/default-for-availability}
    [:template.json-data
     (json/write-value-as-string
      {:congregationBoundary congregation-boundary
       :territories (mapv (fn [territory]
                            {:id (:territory/id territory)
                             :number (:territory/number territory)
                             :location (:territory/location territory)
                             :loaned (:territory/loaned? territory)
                             :staleness (:territory/staleness territory)})
                          territories)})]]))

(defn territory-list-map! [request]
  (territory-list-map (model! request {:fetch-loans? true})))


(defn view [{:keys [territories has-loans? permissions] :as model}]
  (let [styles (:TerritoryListPage (css/modules))]
    (h/html
     [:h1 (i18n/t "TerritoryListPage.title")]
     (when-not (:view-congregation permissions)
       (limited-visibility-help))

     [:div {:class (:map styles)}
      (if has-loans?
        [:div {:class (:placeholder styles)
               :hx-target "this"
               :hx-swap "outerHTML"
               :hx-trigger "load"
               :hx-get (str html/*page-path* "/map")}
         (html/inline-svg "icons/map-location.svg")]
        (territory-list-map model))]

     [:form.pure-form {:class (:search styles)
                       :onsubmit "return false"}
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
       (for [territory (sort-by (comp str :territory/number)
                                (CaseInsensitiveSimpleNaturalComparator/getInstance)
                                territories)]
         [:tr {:data-territory-id (:territory/id territory)
               :data-searchable (-> (str/join "\n" [(:territory/number territory)
                                                    (:territory/region territory)
                                                    (:territory/addresses territory)])
                                    (str/lower-case)
                                    (str/trim))}
          [:td {:class (:number styles)}
           [:a {:href (str html/*page-path* "/" (:territory/id territory))}
            (if (str/blank? (:territory/number territory))
              "-"
              (:territory/number territory))]]
          [:td (:territory/region territory)]
          [:td (:territory/addresses territory)]])]])))

(defn view! [request]
  (view (model! request {:fetch-loans? false})))

(def routes
  ["/congregation/:congregation/territories"
   {:middleware [[html/wrap-page-path ::page]]}
   [""
    {:name ::page
     :get {:handler (fn [request]
                      (-> (view! request)
                          (layout/page! request)
                          (html/response)))}}]

   ["/map"
    {:name ::map
     :conflicting true
     :get {:handler (fn [request]
                      (-> (territory-list-map! request)
                          (html/response)))}}]])

