(ns territory-bro.ui.territory-list-page
  (:require [clojure.string :as str]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.util :as util]
            [territory-bro.ui.assignment :as assignment]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.maps :as maps])
  (:import (java.time LocalDate)))

(defn- last-covered-sort-key [territory]
  (if-some [^LocalDate last-covered (:territory/last-covered territory)]
    (.toEpochDay last-covered)
    0))

(defn assignment-status-sort-key [territory]
  (if-some [assignment (:territory/current-assignment territory)]
    [2 (- (.toEpochDay ^LocalDate (:assignment/start-date assignment)))]
    [1 (last-covered-sort-key territory)]))

(defn sort-territories [sort-column sort-reverse? territories]
  (cond->> territories
    (= :status sort-column) (sort-by assignment-status-sort-key)
    (= :covered sort-column) (sort-by last-covered-sort-key)
    (= :number sort-column) (util/natural-sort-by :territory/number)
    sort-reverse? reverse))

(defn model! [request {:keys [fetch-loans?]}]
  (let [cong-id (get-in request [:path-params :congregation])
        sort-column (if-some [s (get-in request [:params :sort])]
                      (keyword s)
                      :number)
        sort-reverse? (some? (get-in request [:params :reverse]))
        congregation (dmz/get-congregation cong-id)
        today (.toLocalDate (congregation/local-time congregation))
        congregation-boundary (dmz/get-congregation-boundary cong-id)
        territories (->> (dmz/list-territories cong-id)
                         (mapv (fn [territory]
                                 (let [assigned? (some? (:territory/current-assignment territory))
                                       start-date (-> territory :territory/current-assignment :assignment/start-date)
                                       last-covered (-> territory :territory/last-covered)
                                       staleness (cond
                                                   assigned? (util/months-difference start-date today)
                                                   (some? last-covered) (util/months-difference last-covered today)
                                                   :else Integer/MAX_VALUE)]
                                   (-> territory
                                       (assoc :territory/loaned? assigned?)
                                       (assoc :territory/staleness staleness)))))
                         (sort-territories sort-column sort-reverse?))
        territories (cond->> territories
                      fetch-loans? (dmz/enrich-territory-loans cong-id))]
    {:congregation-boundary congregation-boundary
     :territories territories
     :has-loans? (some? (:congregation/loans-csv-url congregation))
     :today today
     :permissions {:view-congregation-temporarily (dmz/allowed? [:view-congregation-temporarily cong-id])}
     :sort-column sort-column
     :sort-reverse? sort-reverse?}))


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


(defn sortable-column-header [label sort-column model]
  (let [styles (:TerritoryListPage (css/modules))
        active? (= sort-column (:sort-column model))
        reverse? (:sort-reverse? model)]
    (h/html
     [:a {:href (str "?sort=" (name sort-column)
                     (when (and active? (not reverse?))
                       "&reverse"))
          :class (:sortable styles)}
      [:span label]
      [:span {:class (html/classes (:sort-icon styles)
                                   (when active?
                                     (:active styles)))}
       (if active?
         (if reverse?
           (html/inline-svg "icons/sort-down.svg" {:data-test-icon "↓"})
           (html/inline-svg "icons/sort-up.svg" {:data-test-icon "↑"}))
         (html/inline-svg "icons/sort.svg" {:data-test-icon "↕"}))]])))

(defn view [{:keys [territories has-loans? permissions today] :as model}]
  (let [styles (:TerritoryListPage (css/modules))]
    (h/html
     [:h1 (i18n/t "TerritoryListPage.title")]
     (when (:view-congregation-temporarily permissions)
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
        [:th {:style {:min-width "4em"}}
         (sortable-column-header (i18n/t "Territory.number") :number model)]
        [:th {:style {:min-width "8em"}}
         (i18n/t "Territory.region")]
        [:th {:style {:min-width "12em"}}
         (i18n/t "Territory.addresses")]
        [:th {:style {:min-width "10em"}}
         (sortable-column-header (i18n/t "Assignment.status") :status model)]
        [:th {:style {:min-width "8em"}}
         (sortable-column-header (i18n/t "Assignment.lastCovered") :covered model)]]]
      [:tbody
       (for [territory territories]
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
          [:td {:style {:max-width "30em"}}
           (:territory/addresses territory)]
          [:td (if-some [assignment (:territory/current-assignment territory)]
                 (assignment/format-status-assigned-duration assignment today)
                 (assignment/format-status-vacant))]
          [:td (when-some [last-covered (:territory/last-covered territory)]
                 (assignment/format-months-ago-with-date last-covered today))]])]])))

(defn view! [request]
  (view (model! request {:fetch-loans? false})))

(def routes
  ["/congregation/:congregation/territories"
   {:middleware [[html/wrap-page-path ::page]
                 dmz/wrap-db-connection]}
   [""
    {:name ::page
     :get {:handler (fn [request]
                      (-> (view! request)
                          (layout/page! request {:main-content-variant :full-width})
                          (html/response)))}}]

   ["/map"
    {:name ::map
     :conflicting true
     :get {:handler (fn [request]
                      (-> (territory-list-map! request)
                          (html/response)))}}]])

