(ns territory-bro.ui.territory-page
  (:require [clojure.string :as str]
            [medley.core :as m]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.infra.util :as util]
            [territory-bro.ui.assignment :as assignment]
            [territory-bro.ui.assignment-history :as assignment-history]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.map-interaction-help :as map-interaction-help]
            [territory-bro.ui.maps :as maps])
  (:import (java.time LocalDate)
           (territory_bro ValidationException)))

(defn- parse-date [s default]
  (or (some-> s LocalDate/parse)
      default))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        territory-id (get-in request [:path-params :territory])
        assignment-id (get-in request [:path-params :assignment])
        congregation (dmz/get-congregation cong-id)
        territory (dmz/get-territory cong-id territory-id)
        do-not-calls (dmz/get-do-not-calls cong-id territory-id)
        assignment-history (dmz/get-territory-assignment-history cong-id territory-id)
        publishers (dmz/list-publishers cong-id)
        today (.toLocalDate (congregation/local-time congregation))]
    (-> {:congregation (select-keys congregation [:congregation/name])
         :territory (-> territory
                        (dissoc :congregation/id)
                        (assoc :territory/do-not-calls do-not-calls))
         :assignment (->> assignment-history
                          (filterv #(= assignment-id (:assignment/id %)))
                          first)
         :assignment-history assignment-history
         :publishers publishers
         :today today
         :form {:publisher (get-in request [:params :publisher] "")
                :start-date (parse-date (get-in request [:params :start-date]) today)
                :end-date (parse-date (get-in request [:params :end-date]) today)
                :returning? (Boolean/parseBoolean (get-in request [:params :returning]))
                :covered? (Boolean/parseBoolean (get-in request [:params :covered]))}
         :permissions {:edit-do-not-calls (dmz/allowed? [:edit-do-not-calls cong-id territory-id])
                       :assign-territory (dmz/allowed? [:assign-territory cong-id territory-id])
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

(defn- common-assignment-form-errors [errors]
  (h/html
   (when (contains? errors :already-assigned)
     [:p.pure-form-message "⚠️ " (i18n/t "Assignment.form.alreadyAssignedError")])
   (when (contains? errors :already-returned)
     [:p.pure-form-message "⚠️ " (i18n/t "Assignment.form.alreadyReturnedError")])))

(defn assign-territory-dialog [{:keys [publishers form today errors]}]
  (let [errors (group-by first errors)
        publisher-not-found? (contains? errors :publisher-not-found)]
    (h/html
     [:dialog
      (common-assignment-form-errors errors)
      [:form.pure-form.pure-form-aligned {:hx-post (str html/*page-path* "/assignments/assign")}
       [:fieldset
        [:legend (i18n/t "Assignment.form.assignTerritory")]

        [:div.pure-control-group
         [:label {:for "publisher-field"}
          (i18n/t "Assignment.form.publisher")]
         [:select#publisher-field {:name "publisher"
                                   :autofocus true
                                   :required true}
          [:option]
          (for [publisher (util/natural-sort-by :publisher/name publishers)]
            [:option {:value (:publisher/name publisher)}
             (:publisher/name publisher)])]
         ;; TODO: use a combobox to more easily select a publisher from a list of a hundred (<datalist> has bad Android support)
         #_[:input#publisher-field {:name "publisher"
                                    :value (:publisher form)
                                    :list "publisher-list"
                                    :autofocus true
                                    :required true
                                    :autocomplete "off"}] ; rely on the publisher list, don't remember old inputs
         (when publisher-not-found?
           (h/html " ⚠️ " [:span.pure-form-message-inline
                           (i18n/t "Assignment.form.publisherNotFound")]))
         #_[:datalist#publisher-list
            (for [publisher (util/natural-sort-by :publisher/name publishers)]
              [:option {:value (:publisher/name publisher)}])]]

        [:div.pure-control-group
         [:label {:for "start-date-field"}
          (i18n/t "Assignment.form.date")]
         [:input#start-date-field {:name "start-date"
                                   :type "date"
                                   :value (str (:start-date form))
                                   :required true
                                   :max (str today)}]]

        [:div.pure-controls
         [:button.pure-button.pure-button-primary {:type "submit"}
          (i18n/t "Assignment.form.assignTerritory")]
         " "
         [:button.pure-button {:type "button"
                               :onclick "this.closest('dialog').close()"}
          (i18n/t "Assignment.form.cancel")]]]]])))

(def ^:private sync-submit-button-js "
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
}")

(defn return-territory-dialog [{:keys [territory form today errors]}]
  (let [assignment (:territory/current-assignment territory)
        start-date (apply m/greatest (conj (:assignment/covered-dates assignment)
                                           (:assignment/start-date assignment)))
        errors (group-by first errors)
        invalid-end-date? (contains? errors :invalid-end-date)]
    (h/html
     [:dialog
      (common-assignment-form-errors errors)
      [:form.pure-form.pure-form-aligned {:hx-post (str html/*page-path* "/assignments/return")
                                          :onchange sync-submit-button-js}
       [:fieldset
        [:legend (i18n/t "Assignment.form.returnTerritory")]
        [:div.pure-control-group
         [:label {:for "end-date-field"}
          (i18n/t "Assignment.form.date")]
         [:input#end-date-field {:name "end-date"
                                 :type "date"
                                 :value (str (:end-date form))
                                 :required true
                                 :min (str start-date)
                                 :max (str today)}]
         (when invalid-end-date? ; HTML form validation should prevent this error, so no user-friendly error message is needed
           (h/html " ⚠️ "))]
        [:div.pure-controls
         [:label.pure-checkbox
          [:input#returning-checkbox {:name "returning"
                                      :type "checkbox"
                                      :value "true"
                                      :checked true
                                      :style {:width "1.5rem"
                                              :height "1.5rem"}}]
          " "
          (i18n/t "Assignment.form.returnTerritoryDescription")]
         [:label.pure-checkbox
          [:input#covered-checkbox {:name "covered"
                                    :type "checkbox"
                                    :value "true"
                                    :checked true
                                    :style {:width "1.5rem"
                                            :height "1.5rem"}}]
          " "
          (i18n/t "Assignment.form.markCoveredDescription")]

         [:button#returning-button.pure-button.pure-button-primary {:type "submit"
                                                                    :autofocus true}
          (i18n/t "Assignment.form.returnTerritory")]
         [:button#covered-button.pure-button.pure-button-primary {:type "submit"
                                                                  :style {:display "none"}}
          (i18n/t "Assignment.form.markCovered")]
         " "
         [:button.pure-button {:type "button"
                               :onclick "this.closest('dialog').close()"}
          (i18n/t "Assignment.form.cancel")]]]]])))

(defn assignment-status [{:keys [territory open-form? today permissions] :as model}]
  (let [styles (:TerritoryPage (css/modules))
        assignment (:territory/current-assignment territory)
        start-date (:assignment/start-date assignment)
        last-covered (:territory/last-covered territory)]
    (h/html
     [:div#assignment-status {:hx-target "this"
                              :hx-swap "outerHTML"
                              :hx-on-htmx-load (when open-form?
                                                 "this.querySelector('dialog').showModal()")}
      (when open-form?
        (if (some? assignment)
          (return-territory-dialog model)
          (assign-territory-dialog model)))

      (when (:assign-territory permissions)
        [:button.pure-button {:hx-get (str html/*page-path* "/assignments/form")
                              :hx-disabled-elt "this"
                              :type "button"
                              :class (:edit-button styles)}
         (if (some? assignment)
           (i18n/t "Assignment.form.return")
           (i18n/t "Assignment.form.assign"))])

      (if (some? assignment)
        (h/html (assignment/format-status-assigned assignment)
                [:br]
                (assignment/format-months-since-date start-date today))
        (h/html (assignment/format-status-vacant)
                (when (some? last-covered)
                  (h/html [:br]
                          (assignment/format-months-since-date last-covered today)))))])))

(defn assignment-form-open [model]
  (assignment-status (assoc model :open-form? true)))

(def assignment-status-updated "assignment-status-updated")

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
           [:th (h/raw (i18n/t "Territory.doNotCalls"))]
           [:td (do-not-calls--viewing model)]]
          [:tr
           [:th (i18n/t "Assignment.status")]
           [:td (assignment-status model)]]]]]

       (when (:share-territory-link permissions)
         [:div {:class (:actions styles)}
          (share-link--closed)])

       (when (:assign-territory permissions)
         (h/html
          [:script {:type "module"}
           (h/raw "
const desktop = window.matchMedia('(min-width: 64em)');
function toggleOpen() {
  document.querySelector('#assignment-history').open = desktop.matches;
}
toggleOpen();
desktop.addEventListener('change', toggleOpen);
")]
          [:details#assignment-history {:open false}
           [:summary {:style {:margin "1rem 0"
                              :font-weight "bold"
                              :cursor "pointer"}}
            (i18n/t "Assignment.assignmentHistory")]
           [:div {:hx-get (str html/*page-path* "/assignments/history")
                  :hx-trigger (str assignment-status-updated " from:body")
                  :hx-target "this"
                  :hx-swap "innerHTML"}
            (assignment-history/view model)]]))]

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
  history.replaceState(null, '', url);
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

(defn assign-territory! [request]
  (let [model (model! request)
        cong-id (get-in request [:path-params :congregation])
        territory-id (get-in request [:path-params :territory])
        publisher-name (-> model :form :publisher)
        publisher-id (publisher/publisher-name->id publisher-name (:publishers model))]
    (try
      (when (nil? publisher-id)
        (throw (ValidationException. [[:publisher-not-found]])))
      (dmz/dispatch! {:command/type :territory.command/assign-territory
                      :congregation/id cong-id
                      :territory/id territory-id
                      :assignment/id (random-uuid)
                      :publisher/id publisher-id
                      :date (-> model :form :start-date)})
      (http-response/see-other (str html/*page-path* "/assignments/status"))
      (catch Exception e
        (forms/validation-error-htmx-response e request model! assignment-form-open)))))

(defn return-territory! [request]
  (let [model (model! request)
        cong-id (get-in request [:path-params :congregation])
        territory-id (get-in request [:path-params :territory])
        assignment-id (-> model :territory :territory/current-assignment :assignment/id)]
    (try
      (when (nil? assignment-id)
        (throw (ValidationException. [[:already-returned]])))
      (dmz/dispatch! {:command/type :territory.command/return-territory
                      :congregation/id cong-id
                      :territory/id territory-id
                      :assignment/id assignment-id
                      :date (-> model :form :end-date)
                      :returning? (-> model :form :returning?)
                      :covered? (-> model :form :covered?)})
      (http-response/see-other (str html/*page-path* "/assignments/status"))
      (catch Exception e
        (forms/validation-error-htmx-response e request model! assignment-form-open)))))

(def routes
  ["/congregation/:congregation/territories/:territory"
   {:middleware [[html/wrap-page-path ::page]
                 dmz/wrap-db-connection]}
   [""
    {:name ::page
     :conflicting true
     :get {:handler (fn [request]
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
    {:get {:handler (fn [request]
                      (-> (do-not-calls--edit! request)
                          (html/response)))}}]

   ["/do-not-calls/save"
    {:post {:handler (fn [request]
                       (-> (do-not-calls--save! request)
                           (html/response)))}}]

   ["/share-link/open"
    {:get {:handler (fn [request]
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

   ["/assignments/status"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (assignment-status)
                          (html/response)
                          ;; Refreshes the history after a territory has been assigned or returned.
                          ;; It would be more logical to emit this header in the assign/return route,
                          ;; but because they do a redirect after post, htmx will ignore any headers
                          ;; that were part of the redirect response.
                          (response/header "hx-trigger" assignment-status-updated)))}}]

   ["/assignments/history"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (assignment-history/view)
                          (html/response)))}}]
   ["/assignments/history/:assignment"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (assignment-history/view-assignment)
                          (html/response)))}
     :delete {:handler (fn [request]
                         ;; TODO: backend logic
                         (http-response/see-other (:uri request)))}}]
   ["/assignments/history/:assignment/edit"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (assignment-history/edit-assignment)
                          (html/response)))}}]

   ["/assignments/form"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (assignment-form-open)
                          (html/response)))}}]

   ["/assignments/assign"
    {:post {:handler (fn [request]
                       (assign-territory! request))}}]

   ["/assignments/return"
    {:post {:handler (fn [request]
                       (return-territory! request))}}]])
