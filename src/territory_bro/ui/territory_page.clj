;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page
  (:require [clojure.string :as str]
            [ring.util.response :as response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.map-interaction-help :as map-interaction-help]
            [territory-bro.ui.maps :as maps])
  (:import (java.time LocalDate Period)))

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
  (let [styles (:TerritoryPage (css/modules))]
    (h/html
     [:div#do-not-calls {:hx-target "this"
                         :hx-swap "outerHTML"}
      (when (:edit-do-not-calls permissions)
        [:button.pure-button {:hx-get (str html/*page-path* "/do-not-calls/edit")
                              :hx-disabled-elt "this"
                              :type "button"
                              :class (:edit-button styles)}
         (i18n/t "TerritoryPage.edit")])
      (:territory/do-not-calls territory)])))

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

(defn assign-territory-dialog [{:keys [publishers today]}]
  (h/html
   [:dialog
    [:form.pure-form.pure-form-aligned {:hx-post (str html/*page-path* "/assignments/assign")}
     [:fieldset
      [:legend "Assign territory"]
      [:div.pure-control-group
       [:label {:for "publisher-field"} "Publisher"]
       [:input#publisher-field {:name "publisher"
                                :list "publisher-list"
                                :autofocus true
                                :required true}]
       [:datalist#publisher-list
        (for [publisher publishers]
          [:option {:value (:publisher/name publisher)}])]]
      [:div.pure-control-group
       [:label {:for "start-date-field"} "Date"]
       [:input#start-date-field {:name "start-date"
                                 :type "date"
                                 :value (str today)
                                 :required true
                                 :max (str today)}]]
      [:div.pure-controls
       [:button.pure-button.pure-button-primary {:type "submit"}
        "Assign territory"]
       " "
       [:button.pure-button {:type "button"
                             :onclick "this.closest('dialog').close()"}
        "Cancel"]]]]]))

(defn return-territory-dialog [{:keys [today latest-assignment]}]
  (h/html
   [:dialog
    [:form.pure-form.pure-form-aligned
     {:hx-post (str html/*page-path* "/assignments/return")
      :onchange "
        const returningButton = this.querySelector('#returning-button');
        const returningCheckbox = this.querySelector('#returning-checkbox');
        const coveredButton = this.querySelector('#covered-button');
        const coveredCheckbox = this.querySelector('#covered-checkbox');
        if (returningCheckbox.checked) {
            returningButton.style.display = '';
            coveredButton.style.display = 'none';
            returningButton.disabled = coveredButton.disabled = false;
        } else if (coveredCheckbox.checked) {
            returningButton.style.display = 'none';
            coveredButton.style.display = '';
            returningButton.disabled = coveredButton.disabled = false;
        } else {
            returningButton.disabled = coveredButton.disabled = true;
        }"}
     [:fieldset
      [:legend "Return territory"]
      [:div.pure-control-group
       [:label {:for "end-date-field"} "Date"]
       [:input#end-date-field {:name "end-date"
                               :type "date"
                               :value (str today)
                               :required true
                               :min (str (:assignment/start-date latest-assignment))
                               :max (str today)}]]
      [:div.pure-controls
       [:label.pure-checkbox
        [:input#returning-checkbox {:name "returning"
                                    :type "checkbox"
                                    :value "true"
                                    :checked true
                                    :style {:width "1.5rem"
                                            :height "1.5rem"}}]
        " "
        "Return the territory to storage"]
       [:label.pure-checkbox
        [:input#covered-checkbox {:name "covered"
                                  :type "checkbox"
                                  :value "true"
                                  :checked true
                                  :style {:width "1.5rem"
                                          :height "1.5rem"}}]
        " "
        "Mark the territory as covered"]
       [:button#returning-button.pure-button.pure-button-primary {:type "submit"
                                                                  :autofocus true}
        "Return territory"]
       [:button#covered-button.pure-button.pure-button-primary {:type "submit"
                                                                :style {:display "none"}}
        "Mark covered"]
       " "
       [:button.pure-button {:type "button"
                             :onclick "this.closest('dialog').close()"}
        "Cancel"]]]]]))

(def fake-time (LocalDate/of 2024 10 29))
(def fake-assignment-model-assigned
  {:today fake-time
   :latest-assignment {:publisher/name "John Doe"
                       :assignment/start-date (-> fake-time (.minusMonths 4) (.minusDays 18))}})
(def fake-assignment-model-vacant
  {:today fake-time
   :latest-assignment {:publisher/name "John Doe"
                       :assignment/start-date (-> fake-time (.minusMonths 4) (.minusDays 18))
                       :assignment/end-date (-> fake-time (.minusMonths 2) (.minusDays 5))}
   :publishers [{:publisher/name "Andrew"}
                {:publisher/name "Bartholomew"}
                {:publisher/name "James, son of Zebedee"}
                {:publisher/name "James, son of Alphaeus"}
                {:publisher/name "John"}
                {:publisher/name "Matthew"}
                {:publisher/name "Matthias"}
                {:publisher/name "Peter"}
                {:publisher/name "Philip"}
                {:publisher/name "Simon"}
                {:publisher/name "Thaddaeus"}
                {:publisher/name "Thomas"}]})

(defn months-difference [^LocalDate start ^LocalDate end]
  (.toTotalMonths (Period/between start end)))

(defn- nowrap [s]
  (h/html [:span {:style {:white-space "nowrap"}}
           s]))

(defn assignment-status [{:keys [open-form? today latest-assignment] :as model}]
  (let [styles (:TerritoryPage (css/modules))
        start-date (:assignment/start-date latest-assignment)
        end-date (:assignment/end-date latest-assignment)
        assigned? (and (some? start-date)
                       (nil? end-date))]
    (if assigned?
      (h/html
       [:div#assignment-status {:hx-target "this"
                                :hx-swap "outerHTML"
                                :hx-on-htmx-load (when open-form?
                                                   "this.querySelector('dialog').showModal()")}
        (when open-form?
          (return-territory-dialog model))
        [:button.pure-button {:hx-get (str html/*page-path* "/assignments/return")
                              :hx-disabled-elt "this"
                              :type "button"
                              :class (:edit-button styles)}
         "Return"]
        [:span {:style {:color "red"}}
         "Assigned"]
        " to " (:publisher/name latest-assignment)
        [:br]
        "(" (months-difference start-date today) " months, since " (nowrap start-date) ")"])

      (h/html
       [:div#assignment-status {:hx-target "this"
                                :hx-swap "outerHTML"
                                :hx-on-htmx-load (when open-form?
                                                   "this.querySelector('dialog').showModal()")}
        (when open-form?
          (assign-territory-dialog model))
        [:button.pure-button {:hx-get (str html/*page-path* "/assignments/assign")
                              :hx-disabled-elt "this"
                              :type "button"
                              :class (:edit-button styles)}
         "Assign"]
        [:span {:style {:color "blue"}}
         "Up for grabs"]
        [:br]
        "(" (months-difference end-date today) " months, since " (nowrap end-date) ")"]))))

(def fake-assignment-model-history
  {:assignment-history []})

(defn assignment-history [{:keys [assignment-history]}]
  ;; TODO: calculate rows based on assignment-history
  (let [rows [{:type :assignment
               :grid-row 1
               :grid-span 4}
              {:type :duration
               :grid-row 1
               :assigned? true
               :months 2}
              {:type :event
               :grid-row 2
               :date (-> fake-time (.minusMonths 2) (.minusDays 4))
               :covered? true}
              {:type :duration
               :grid-row 3
               :assigned? true
               :months 4}
              {:type :event
               :grid-row 4
               :date (-> fake-time (.minusMonths 6) (.minusDays 16))
               :assigned? true
               :publisher/name "John Doe"}
              {:type :duration
               :grid-row 5
               :assigned? false
               :months 8}
              {:type :assignment
               :grid-row 6
               :grid-span 3}
              {:type :event
               :grid-row 6
               :date (-> fake-time (.minusMonths 14) (.minusDays 20))
               :returned? true
               :covered? true}
              {:type :duration
               :grid-row 7
               :assigned? true
               :months 2}
              {:type :event
               :grid-row 8
               :date (-> fake-time (.minusMonths 16) (.minusDays 30))
               :assigned? true
               :publisher/name "Joe Blow"}]]
    (h/html
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
       (for [{:keys [grid-row grid-span] :as row} rows]
         (case (:type row)
           :assignment
           (h/html
            ;; XXX: workaround to Hiccup style attribute bug https://github.com/weavejester/hiccup/issues/211
            [:div {:style (identity {:grid-column "timeline-start / timeline-end"
                                     :grid-row (str grid-row " / " (+ grid-row grid-span))
                                     :background "linear-gradient(to top, #3330, #333f 1.5rem, #333f calc(100% - 1.5rem), #3330)"})}]
            [:div {:style (identity {:grid-column "controls-start / controls-end"
                                     :grid-row grid-row
                                     :text-align "right"})}
             [:a {:href "#"
                  :onclick "return false"}
              "Edit"]])

           :duration
           (h/html
            [:div {:style (identity {:grid-column "time-start / time-end"
                                     :grid-row grid-row
                                     :white-space "nowrap"
                                     :text-align "center"
                                     :padding "0.7rem 0"
                                     :color (when-not (:assigned? row)
                                              "#999")})}
             (:months row) " months"])

           :event
           (h/html
            [:div {:style (identity {:grid-column "time-start / time-end"
                                     :grid-row grid-row
                                     :white-space "nowrap"})}
             (:date row)]
            [:div {:style (identity {:grid-column "event-start / event-end"
                                     :grid-row grid-row
                                     :display "flex"
                                     :flex-direction "column"
                                     :gap "0.25rem"})}
             (when (:returned? row)
               [:div "📥 Returned "])
             (when (:covered? row)
               [:div "✅ Covered"])
             (when (:assigned? row)
               [:div "⤴️ Assigned to " (:publisher/name row)])])))]])))

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
          (when (:dev config/env)
            [:tr
             [:th "Status"]
             [:td (assignment-status fake-assignment-model-assigned)]])]]]

       (when (:share-territory-link permissions)
         [:div {:class (:actions styles)}
          (share-link--closed)])

       (when (:dev config/env)
         (assignment-history fake-assignment-model-history))]

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
                          (html/response)))}}]

   ["/assignments/assign"
    {:middleware [dmz/wrap-db-connection]
     :get {:handler (fn [request]
                      (let [model (model! request)]
                        (-> (assignment-status (assoc fake-assignment-model-vacant
                                                      :open-form? true))
                            (html/response))))}
     :post {:handler (fn [request]
                       (let [model (model! request)]
                         (-> (assignment-status fake-assignment-model-assigned)
                             (html/response))))}}]

   ["/assignments/return"
    {:middleware [dmz/wrap-db-connection]
     :get {:handler (fn [request]
                      (let [model (model! request)]
                        (-> (assignment-status (assoc fake-assignment-model-assigned
                                                      :open-form? true))
                            (html/response))))}
     :post {:handler (fn [request]
                       (let [model (model! request)]
                         (-> (assignment-status fake-assignment-model-vacant)
                             (html/response))))}}]])
