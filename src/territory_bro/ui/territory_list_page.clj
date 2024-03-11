;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn model! [request]
  (let [congregation (:body (api/get-congregation request))]
    {:territories (->> (:territories congregation)
                       ;;  TODO: use natural sort
                       (sort-by :number))}))

(defn view [{:keys [territories]}]
  (let [styles (:TerritoryListPage (css/modules))]
    (h/html
     [:h1 (i18n/t "TerritoryListPage.title")]
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
