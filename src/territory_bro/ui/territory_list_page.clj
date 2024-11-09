(ns territory-bro.ui.territory-list-page
  (:require [clojure.string :as str]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.util :as util]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.maps :as maps]))

(defn model! [request {:keys [fetch-loans?]}]
  (let [cong-id (get-in request [:path-params :congregation])
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
                                       (assoc :territory/staleness staleness))))))
        territories (cond->> territories
                      fetch-loans? (dmz/enrich-territory-loans cong-id))]
    {:congregation-boundary congregation-boundary
     :territories territories
     :has-loans? (some? (:congregation/loans-csv-url congregation))
     :today today
     :permissions {:view-congregation-temporarily (dmz/allowed? [:view-congregation-temporarily cong-id])}}))


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
         (i18n/t "Territory.number")]
        [:th {:style {:min-width "8em"}}
         (i18n/t "Territory.region")]
        [:th {:style {:min-width "12em"}}
         (i18n/t "Territory.addresses")]
        [:th {:style {:min-width "10em"}}
         "Status"] ; TODO: i18n
        [:th {:style {:min-width "8em"}}
         "Last covered"]]] ; TODO: i18n
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
                 (h/html
                  [:span {:style {:color "red"}} "Assigned"] ; TODO: i18n
                  (-> " to {{name}}"
                      (str/replace "{{name}}" (or (:publisher/name assignment)
                                                  "[deleted]")))
                  " for " (util/months-difference (:assignment/start-date assignment) today) " months")
                 (h/html
                  [:span {:style {:color "blue"}} "Up for grabs"]))] ; TODO: i18n
          [:td (when-some [last-covered (:territory/last-covered territory)]
                 (h/html
                  (util/months-difference last-covered today) " months ago (" (html/nowrap last-covered) ")"))]])]])))

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

