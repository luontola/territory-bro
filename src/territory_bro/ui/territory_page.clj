;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [ring.util.response :as response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.map-interaction-help :as map-interaction-help]
            [territory-bro.ui.maps :as maps])
  (:import (java.time LocalDate)))

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
           [:td (do-not-calls--viewing model)]]

          ;; TODO: POC - status=available
          (when (:dev config/env)
            [:tr
             [:th "Status"]
             [:td
              [:button.pure-button {:onclick "document.querySelector('#assign-territory-dialog').showModal()"
                                    :type "button"
                                    :style {:float "right"
                                            :font-size "70%"}}
               "Assign"]
              [:span {:style {:color "blue"}}
               "Up for grabs"]
              [:br]
              "(6 months, since "
              (-> (LocalDate/now) (.minusMonths 6) (.minusDays 7))
              ")"]])

          ;; TODO: POC - status=assigned
          (when (:dev config/env)
            [:tr
             [:th "Status"]
             [:td
              [:button.pure-button {:onclick "document.querySelector('#return-territory-dialog').showModal()"
                                    :type "button"
                                    :style {:float "right"
                                            :font-size "70%"}}
               "Return"]
              [:span {:style {:color "red"}} "Assigned"] " to John Doe"
              [:br]
              "(4 months, since "
              (-> (LocalDate/now) (.minusMonths 4) (.minusDays 18))
              ")"]])]]

        ;; TODO: POC - assign territory form
        (when (:dev config/env)
          [:dialog#assign-territory-dialog
           [:form.pure-form.pure-form-aligned {:method "dialog"} ; TODO: submit form, remove dialog from DOM with htmx
            [:fieldset
             [:legend "Assign territory"]
             [:div.pure-control-group
              [:label {:for "publisher"} "Publisher"]
              [:input#publisher {:autofocus true
                                 :required true
                                 :list "publisher-list"}]
              [:datalist#publisher-list
               [:option {:value "Andrew"}]
               [:option {:value "Bartholomew"}]
               [:option {:value "James, son of Zebedee"}]
               [:option {:value "James, son of Alphaeus"}]
               [:option {:value "John"}]
               [:option {:value "Matthew"}]
               [:option {:value "Matthias"}]
               [:option {:value "Peter"}]
               [:option {:value "Philip"}]
               [:option {:value "Simon"}]
               [:option {:value "Thaddaeus"}]
               [:option {:value "Thomas"}]]]
             [:div.pure-control-group
              [:label {:for "assign-date"} "Date"]
              [:input#assign-date {:type "date"
                                   :value (str (LocalDate/now))
                                   :required true
                                   :max (str (LocalDate/now))}]]
             [:div.pure-controls
              [:button.pure-button.pure-button-primary {:type "submit"}
               "Assign territory"]
              " "
              [:button.pure-button {:type "submit"
                                    :formmethod "dialog"
                                    :formnovalidate true}
               "Cancel"]]]]])

        ;; TODO: POC - return territory form
        (when (:dev config/env)
          [:dialog#return-territory-dialog
           [:form.pure-form.pure-form-aligned {:method "dialog" ; TODO: submit form, remove dialog from DOM with htmx
                                               :onchange "
                                               const submit = document.querySelector('#return-territory-dialog .pure-button-primary');
                                               if (document.getElementById('return').checked) {
                                                   submit.textContent = 'Return territory'
                                                   submit.disabled = false
                                               } else if (document.getElementById('cover').checked) {
                                                   submit.textContent = 'Mark covered'
                                                   submit.disabled = false
                                               } else {
                                                   submit.disabled = true
                                               }"}
            [:fieldset
             [:legend "Return territory"]
             [:div.pure-control-group
              [:label {:for "return-date"} "Date"]
              [:input#return-date {:type "date"
                                   :value (str (LocalDate/now))
                                   :required true
                                   :min (-> (LocalDate/now) (.minusMonths 4) (.minusDays 18))
                                   :max (str (LocalDate/now))}]]
             [:div.pure-controls
              [:label.pure-checkbox
               [:input#return {:type "checkbox"
                               :checked true
                               :style {:width "1.5rem"
                                       :height "1.5rem"}}]
               " Return the territory to storage"]
              [:label.pure-checkbox
               [:input#cover {:type "checkbox"
                              :checked true
                              :style {:width "1.5rem"
                                      :height "1.5rem"}}]
               " Mark the territory as covered"]
              [:button.pure-button.pure-button-primary {:type "submit"
                                                        :autofocus true}
               "Return territory"]
              " "
              [:button.pure-button {:type "submit"
                                    :formmethod "dialog"
                                    :formnovalidate true}
               "Cancel"]]]]])]

       (when (:share-territory-link permissions)
         [:div {:class (:actions styles)}
          (share-link--closed)])

       ;; TODO: POC - assignment history
       (when (:dev config/env)
         [:details {:open false}
          [:summary {:style {:margin "1rem 0"
                             :font-weight "bold"
                             :cursor "pointer"}}
           "Assignment history"]
          [:div {:style {:display "grid"
                         :grid-template-columns "[time-start] min-content [time-end timeline-start] 4px [timeline-end event-start] 1fr [event-end controls-start] min-content [controls-end]"
                         :gap "0.5rem"
                         :width "fit-content"
                         :margin "1rem 0"}}

           ;; TODO: POC - ongoing assignment
           [:div {:style {:grid-column "timeline-start / timeline-end"
                          :grid-row "1 / 5"
                          :background "linear-gradient(to top, #3330, #333f 1.5rem, #333f calc(100% - 1.5rem), #3330)"}}]
           [:div {:style {:grid-column "controls-start / controls-end"
                          :grid-row 1
                          :text-align "right"}}
            [:a {:href "#"
                 :onclick "return false"}
             "Edit"]]

           [:div {:style {:grid-column "time-start / time-end"
                          :grid-row 1
                          :white-space "nowrap"
                          :text-align "center"
                          :padding "0.7rem 0"}}
            "2 months"]

           [:div {:style {:grid-column "time-start / time-end"
                          :grid-row 2
                          :white-space "nowrap"}}
            (str (-> (LocalDate/now) (.minusMonths 2) (.minusDays 4)))]
           [:div {:style {:grid-column "event-start / event-end"
                          :grid-row 2}}
            "✅ Covered"]

           [:div {:style {:grid-column "time-start / time-end"
                          :grid-row 3
                          :white-space "nowrap"
                          :text-align "center"
                          :padding "0.7rem 0"}}
            "4 months"]

           [:div {:style {:grid-column "time-start / time-end"
                          :grid-row 4
                          :white-space "nowrap"}}
            (str (-> (LocalDate/now) (.minusMonths 6) (.minusDays 16)))]
           [:div {:style {:grid-column "event-start / event-end"
                          :grid-row 4}}
            "⤴️ Assigned to John Doe"]

           [:div {:style {:grid-column "time-start / time-end"
                          :grid-row 5
                          :white-space "nowrap"
                          :text-align "center"
                          :padding "0.7rem 0"
                          :color "#999"}}
            "8 months"]

           ;; TODO: POC - completed assignment
           [:div {:style {:grid-column "timeline-start / timeline-end"
                          :grid-row "6 / 9"
                          :background "linear-gradient(to top, #3330, #333f 1.5rem, #333f calc(100% - 1.5rem), #3330)"}}]
           [:div {:style {:grid-column "controls-start / controls-end"
                          :grid-row 6
                          :text-align "right"}}
            [:a {:href "#"
                 :onclick "return false"}
             "Edit"]]

           [:div {:style {:grid-column "time-start / time-end"
                          :grid-row 6
                          :white-space "nowrap"
                          #_#_:align-self "center"}}
            (str (-> (LocalDate/now) (.minusMonths 14) (.minusDays 20)))]
           [:div {:style {:grid-column "event-start / event-end"
                          :grid-row 6}}
            "📥 Returned "
            [:div {:style {:margin-top "0.25rem"}} ; add some vertical spacing between emojis (line-height would add space also above the first row, which doesn't look good)
             "✅ Covered"]]

           [:div {:style {:grid-column "time-start / time-end"
                          :grid-row 7
                          :white-space "nowrap"
                          :text-align "center"
                          :padding "0.7rem 0"}}
            "2 months"]

           [:div {:style {:grid-column "time-start / time-end"
                          :grid-row 8
                          :white-space "nowrap"}}
            (str (-> (LocalDate/now) (.minusMonths 16) (.minusDays 30)))]
           [:div {:style {:grid-column "event-start / event-end"
                          :grid-row 8}}
            "⤴️ Assigned to Joe Blow"]]])]

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
   [:meta {:property "og:image"
           :content (-> (geometry/parse-wkt (:territory/location territory))
                        (geometry/enclosing-tms-tile)
                        (geometry/openstreetmap-tms-url))}]
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
