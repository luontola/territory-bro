;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [ring.util.response :as response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.map-interaction-help :as map-interaction-help]
            [territory-bro.ui.maps :as maps]))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        territory-id (get-in request [:path-params :territory])
        congregation (dmz/get-congregation cong-id)
        territory (dmz/get-territory cong-id territory-id)
        do-not-calls (dmz/get-do-not-calls cong-id territory-id)]
    (-> {:congregation (select-keys congregation [:congregation/name])
         :territory (-> territory
                        (dissoc :congregation/id)
                        (assoc :territory/do-not-calls do-not-calls))
         :permissions {:edit-do-not-calls (dmz/allowed? [:edit-do-not-calls cong-id territory-id])
                       :share-territory-link (dmz/allowed? [:share-territory-link cong-id territory-id])}}
        (merge (map-interaction-help/model request)))))


(defn do-not-calls--viewing [{:keys [territory permissions]}]
  (h/html
   [:div#do-not-calls {:hx-target "this"
                       :hx-swap "outerHTML"}
    (when (:edit-do-not-calls permissions)
      [:button.pure-button {:hx-get (str html/*page-path* "/do-not-calls/edit")
                            :hx-disabled-elt "this"
                            :type "button"
                            :style {:float "right"
                                    :font-size "70%"}}
       (i18n/t "TerritoryPage.edit")])
    (:territory/do-not-calls territory)]))

(defn do-not-calls--editing [{:keys [territory]}]
  (h/html
   [:form#do-not-calls.pure-form {:hx-target "this"
                                  :hx-swap "outerHTML"
                                  :hx-post (str html/*page-path* "/do-not-calls/save")
                                  :hx-disabled-elt "#do-not-calls :is(textarea, button)"}
    [:textarea.pure-input-1 {:name "do-not-calls"
                             :rows 5
                             :autofocus true}
     (:territory/do-not-calls territory)]
    [:button.pure-button.pure-button-primary {:type "submit"}
     (i18n/t "TerritoryPage.save")]]))

(defn do-not-calls--edit! [request]
  (do-not-calls--editing (model! request)))

(defn do-not-calls--save! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        territory-id (get-in request [:path-params :territory])
        do-not-calls (str/trim (str (get-in request [:params :do-not-calls])))]
    (dmz/dispatch! {:command/type :do-not-calls.command/save-do-not-calls
                    :congregation/id cong-id
                    :territory/id territory-id
                    :territory/do-not-calls do-not-calls}))
  (do-not-calls--viewing (model! request)))


(defn share-link [{:keys [open? link]}]
  (let [styles (:TerritoryPage (css/modules))
        ;; if the user has changed the language, stop caching "/share-link/open"
        cache-buster (str "?v=" (name i18n/*lang*))]
    (h/html
     [:form.pure-form {:hx-target "this"
                       :hx-swap "outerHTML"}
      [:button.pure-button {:hx-get (str html/*page-path* "/share-link/" (if open? "close" (str "open" cache-buster)))
                            :type "button"
                            :class (when open?
                                     "pure-button-active")
                            :aria-expanded (if open? "true" "false")}
       (html/inline-svg "icons/share.svg")
       " "
       (i18n/t "TerritoryPage.shareLink.button")]

      (when open?
        [:div {:class (:sharePopup styles)}
         [:button.pure-button {:hx-get (str html/*page-path* "/share-link/close")
                               :type "button"
                               :class (:closeButton styles)
                               :aria-label (i18n/t "TerritoryPage.shareLink.closePopup")
                               :title (i18n/t "TerritoryPage.shareLink.closePopup")}
          (html/inline-svg "icons/close.svg")]

         [:label {:htmlFor "share-link"}
          (i18n/t "TerritoryPage.shareLink.description")]

         [:div {:class (:shareLink styles)}
          [:input#share-link {:type "text"
                              :value link
                              :readonly true
                              :style {:color "unset"
                                      :background-color "unset"}}]
          [:button#copy-share-link.pure-button {:type "button"
                                                :data-clipboard-target "#share-link"
                                                :aria-label (i18n/t "TerritoryPage.shareLink.copy")
                                                :title (i18n/t "TerritoryPage.shareLink.copy")}
           (html/inline-svg "icons/copy.svg")]]])])))

(defn share-link--open! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        territory-id (get-in request [:path-params :territory])
        share (dmz/share-territory-link cong-id territory-id)]
    (share-link {:open? true
                 :link (:url share)})))

(defn share-link--closed []
  (share-link {:open? false}))


(defn view [{:keys [territory permissions] :as model}]
  (let [styles (:TerritoryPage (css/modules))]
    (h/html
     [:h1 (-> (i18n/t "TerritoryPage.title")
              (str/replace "{{number}}" (:territory/number territory)))]
     [:div.pure-g
      [:div.pure-u-1.pure-u-sm-2-3.pure-u-md-1-2.pure-u-lg-1-3.pure-u-xl-1-4
       [:div {:class (:details styles)}
        [:table.pure-table.pure-table-horizontal
         [:tbody
          [:tr
           [:th (i18n/t "Territory.number")]
           [:td (:territory/number territory)]]
          [:tr
           [:th (i18n/t "Territory.region")]
           [:td (:territory/region territory)]]
          [:tr
           [:th (i18n/t "Territory.addresses")]
           [:td (:territory/addresses territory)]]
          [:tr
           [:th (h/raw (i18n/t "TerritoryPage.doNotCalls"))]
           [:td (do-not-calls--viewing model)]]]]]

       (when (:share-territory-link permissions)
         [:div {:class (:actions styles)}
          (share-link--closed)])]

      [:div.pure-u-1.pure-u-lg-2-3.pure-u-xl-3-4
       [:div {:class (:map styles)}
        [:territory-map {:territory-location (:territory/location territory)
                         :map-raster maps/default-for-availability}]]
       [:div.no-print
        (map-interaction-help/view model)]]])))

(def share-key-cleanup-js
  (h/raw "
const url = new URL(document.location.href);
if (url.searchParams.has('share-key')) {
  url.searchParams.delete('share-key');
  history.replaceState(null, '', url)
}
"))

(defn head [{:keys [congregation territory]}]
  (h/html
   [:meta {:property "og:type"
           :content "website"}]
   [:meta {:property "og:title"
           :content (->> [(-> (i18n/t "TerritoryPage.title")
                              (str/replace "{{number}}" (:territory/number territory)))
                          (:territory/region territory)
                          (:congregation/name congregation)]
                         (mapv str/trim)
                         (remove str/blank?)
                         (interpose " - ")
                         (apply str))}]
   [:meta {:property "og:description"
           :content (->> (str/split (:territory/addresses territory) #"\n")
                         (mapv str/trim)
                         (remove str/blank?)
                         (interpose ", ")
                         (apply str))}]
   ;; TODO: show a territory map, e.g. the closest OpenLayers TMS tile which contains the territory
   [:meta {:property "og:image"
           :content (str (:public-url config/env) (get html/public-resources "/assets/logo-big.*.svg"))}]
   [:script {:type "module"}
    share-key-cleanup-js]))

(def routes
  ["/congregation/:congregation/territories/:territory"
   {:middleware [[html/wrap-page-path ::page]]}
   [""
    {:name ::page
     :conflicting true
     :get {:middleware [dmz/wrap-db-connection]
           :handler (fn [request]
                      ;; When a share is opened by an instant messenger app such as WhatsApp,
                      ;; the HTTP client may not have cookies enabled.
                      ;; Passing the share-key as a query parameter enables opening the share without cookies.
                      ;; To avoid somebody copy-pasting the non-share URL, and to clean up the URL,
                      ;; the query parameter is removed with JavaScript when the page is opened.
                      (binding [dmz/*state* (dmz/open-share-without-cookies dmz/*state*
                                                                            (get-in request [:path-params :congregation])
                                                                            (get-in request [:path-params :territory])
                                                                            (get-in request [:params :share-key]))]
                        (let [model (model! request)]
                          (-> (view model)
                              (layout/page! request {:main-content-variant :full-width
                                                     :head (head model)})
                              (html/response)))))}}]

   ["/do-not-calls/edit"
    {:get {:middleware [dmz/wrap-db-connection]
           :handler (fn [request]
                      (-> (do-not-calls--edit! request)
                          (html/response)))}}]

   ["/do-not-calls/save"
    {:post {:middleware [dmz/wrap-db-connection]
            :handler (fn [request]
                       (-> (do-not-calls--save! request)
                           (html/response)))}}]

   ["/share-link/open"
    {:get {:middleware [dmz/wrap-db-connection]
           :handler (fn [request]
                      (-> (share-link--open! request)
                          (html/response)
                          ;; avoid creating lots of new shares if the user clicks the share button repeatedly
                          (response/header "Cache-Control" "private, max-age=300, must-revalidate")
                          ;; this is a GET request to enable caching, but it actually writes new events to the database
                          (assoc ::middleware/mutative-operation? true)))}}]

   ["/share-link/close"
    {:get {:handler (fn [_request]
                      (-> (share-link--closed)
                          (html/response)))}}]])
