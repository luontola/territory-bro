;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [ring.util.response :as response]
            [territory-bro.api :as api]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.map-interaction-help :as map-interaction-help]))

(defn model! [request]
  (let [congregation (:body (api/get-congregation request))
        territory (:body (api/get-territory request))]
    (-> {:territory territory
         :permissions (-> (:permissions congregation)
                          (select-keys [:editDoNotCalls :shareTerritoryLink]))}
        (merge (map-interaction-help/model request)))))

(defn do-not-calls--viewing [{:keys [territory permissions]}]
  (h/html
   [:div {:hx-target "this"
          :hx-swap "outerHTML"}
    (when (:editDoNotCalls permissions)
      [:button.pure-button {:hx-get (str html/*page-path* "/do-not-calls/edit")
                            :hx-disabled-elt "this"
                            :type "button"
                            :style "float: right; font-size: 70%;"}
       (i18n/t "TerritoryPage.edit")])
    (or (:doNotCalls territory)
        "-")]))

(defn do-not-calls--editing [{:keys [territory]}]
  (h/html
   [:form.do-not-calls.pure-form {:hx-target "this"
                                  :hx-swap "outerHTML"
                                  :hx-post (str html/*page-path* "/do-not-calls/save")
                                  :hx-disabled-elt ".do-not-calls :is(textarea, button)"}
    [:textarea.pure-input-1 {:name "do-not-calls"
                             :rows 5
                             :autofocus true}
     (:doNotCalls territory)]
    [:button.pure-button.pure-button-primary {:type "submit"}
     (i18n/t "TerritoryPage.save")]]))

(defn do-not-calls--edit! [request]
  (do-not-calls--editing (model! request)))

(defn do-not-calls--save! [request]
  (api/edit-do-not-calls request)
  (do-not-calls--viewing (model! request)))


(defn share-link [{:keys [open? link]}]
  (let [styles (:TerritoryPage (css/modules))]
    (h/html
     [:form.pure-form {:hx-target "this"
                       :hx-swap "outerHTML"}
      [:button.pure-button {:hx-get (str html/*page-path* "/share-link/" (if open? "close" "open"))
                            :type "button"
                            :class (when open?
                                     "pure-button-active")
                            :aria-expanded (if open? "true" "false")}
       [:i.fa-solid.fa-share-nodes]
       " "
       (i18n/t "TerritoryPage.shareLink.button")]

      (when open?
        [:div {:class (:sharePopup styles)}
         [:button.pure-button {:hx-get (str html/*page-path* "/share-link/close")
                               :type "button"
                               :class (:closeButton styles)
                               :aria-label (i18n/t "TerritoryPage.shareLink.closePopup")
                               :title (i18n/t "TerritoryPage.shareLink.closePopup")}
          [:i.fa-solid.fa-xmark]]

         [:label {:htmlFor "share-link"}
          (i18n/t "TerritoryPage.shareLink.description")]

         [:div {:class (:shareLink styles)}
          [:input#share-link {:type "text"
                              :value link
                              :readonly true
                              :style "color: unset; background-color: unset;"}]
          ;; TODO: should copy link to clipboard
          [:button#copy-share-link.pure-button {:type "button"
                                                :data-clipboard-target "#share-link"
                                                :aria-label (i18n/t "TerritoryPage.shareLink.copy")
                                                :title (i18n/t "TerritoryPage.shareLink.copy")}
           [:i.fa-solid.fa-copy]]]])])))

(defn share-link--open! [request]
  (let [share (:body (api/share-territory-link request))]
    (share-link {:open? true
                 :link (:url share)})))

(defn share-link--closed []
  (share-link {:open? false}))


(defn view [{:keys [territory permissions] :as model}]
  (let [styles (:TerritoryPage (css/modules))]
    (h/html
     [:DemoDisclaimer] ; TODO
     [:h1 (-> (i18n/t "TerritoryPage.title")
              (str/replace "{{number}}" (:number territory)))]
     [:div.pure-g
      [:div.pure-u-1.pure-u-sm-2-3.pure-u-md-1-2.pure-u-lg-1-3.pure-u-xl-1-4
       [:div {:class (:details styles)}
        [:table.pure-table.pure-table-horizontal
         [:tbody
          [:tr
           [:th (i18n/t "Territory.number")]
           [:td (:number territory)]]
          [:tr
           [:th (i18n/t "Territory.region")]
           [:td (:region territory)]]
          [:tr
           [:th (i18n/t "Territory.addresses")]
           [:td (:addresses territory)]]
          [:tr
           [:th (i18n/t "TerritoryPage.doNotCalls")]
           [:td (do-not-calls--viewing model)]]]]]

       (when (:shareTerritoryLink permissions)
         [:div {:class (:actions styles)}
          (share-link--closed)])]

      [:div.pure-u-1.pure-u-lg-2-3.pure-u-xl-3-4
       [:div {:class (:map styles)}
        [:territory-map {:location (:location territory)
                         :map-raster "osmhd"
                         :printout false}]]
       [:div.no-print
        (map-interaction-help/view model)]]])))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/congregation/:congregation/territories/:territory"
   {:middleware [[html/wrap-page-path ::territory-page]]}
   [""
    {:name ::territory-page
     :get {:handler (fn [request]
                      (-> (view! request)
                          (layout/page! request)
                          (html/response)))}}]

   ["/do-not-calls/edit"
    {:get {:handler (fn [request]
                      (-> (do-not-calls--edit! request)
                          (html/response)))}}]

   ["/do-not-calls/save"
    {:post {:handler (fn [request]
                       (-> (do-not-calls--save! request)
                           (html/response)))}}]

   ["/share-link/open"
    {:get {:middleware [middleware/wrap-always-refresh-projections]
           :handler (fn [request]
                      (-> (share-link--open! request)
                          (html/response)
                          ;; avoid creating lots of new shares if the user clicks the share button repeatedly
                          (response/header "Cache-Control" "max-age=300, must-revalidate")))}}]

   ["/share-link/close"
    {:get {:handler (fn [_request]
                      (-> (share-link--closed)
                          (html/response)))}}]])
