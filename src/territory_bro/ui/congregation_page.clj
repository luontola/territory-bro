(ns territory-bro.ui.congregation-page
  (:require [clojure.math :as math]
            [clojure.string :as str]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout])
  (:import (java.time LocalDate)
           (java.time.temporal ChronoUnit)))

(defn- average [numbers]
  (when-not (empty? numbers)
    (math/round (/ (apply + numbers)
                   (count numbers)))))

(defn- covered-after? [^LocalDate cutoff-date]
  (fn [territory]
    (when-some [last-covered (:territory/last-covered territory)]
      (. cutoff-date isBefore last-covered))))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        congregation (dmz/get-congregation cong-id)
        territories (dmz/list-raw-territories cong-id)
        publishers (dmz/list-publishers cong-id)
        milestones (dmz/milestones cong-id)
        today (.toLocalDate (congregation/local-time congregation))
        cutoff-6-months (.minusMonths today 6)
        cutoff-12-months (.minusMonths today 12)
        assignment-durations-in-days (into []
                                           (comp (mapcat #(vals (:territory/assignments %)))
                                                 (filter :assignment/end-date)
                                                 (filter :assignment/covered-dates)
                                                 (filter #(. cutoff-12-months isBefore (:assignment/end-date %)))
                                                 (map #(max 1 (. ChronoUnit/DAYS between (:assignment/start-date %) (:assignment/end-date %)))))
                                           territories)]
    {:congregation (select-keys congregation [:congregation/name])
     :getting-started {:congregation-boundary? (some? (dmz/get-congregation-boundary cong-id))
                       :territories? (not (empty? territories))
                       :publishers? (not (empty? publishers))
                       :territories-assigned? (contains? milestones :territory-assigned)
                       :share-link-created? (contains? milestones :share-link-created)
                       :qr-code-scanned? (contains? milestones :qr-code-scanned)}
     :statistics {:territories (count territories)
                  :assigned-territories (count (filterv :territory/current-assignment territories))
                  :covered-in-past-6-months (count (filterv (covered-after? cutoff-6-months) territories))
                  :covered-in-past-12-months (count (filterv (covered-after? cutoff-12-months) territories))
                  :average-assignment-days (average assignment-durations-in-days)}
     :permissions {:view-printouts-page (dmz/view-printouts-page? cong-id)
                   :view-settings-page (dmz/view-settings-page? cong-id)
                   :view-statistics (dmz/allowed? [:view-congregation cong-id])
                   :view-getting-started (dmz/allowed? [:gis-access cong-id])}}))


(defn- count-and-percentage [count total]
  (let [percentage (if (zero? total)
                     0
                     (math/round (* 100 (/ count total))))]
    (str count " (" percentage "\u00A0%)")))

(defn statistics [{:keys [statistics permissions]}]
  (when (:view-statistics permissions)
    (let [styles (:CongregationPage (css/modules))]
      (info-box/view
       {:title (i18n/t "Statistics.title")}
       (h/html
        [:ul {:class (:statistics styles)}
         [:li {}
          (i18n/t "Statistics.territories") ": " (:territories statistics)]
         [:li {}
          (i18n/t "Statistics.assignedTerritories") ": "
          (count-and-percentage (:assigned-territories statistics)
                                (:territories statistics))]
         [:li {}
          (i18n/t "Statistics.coveredInPast6Months") ": "
          (count-and-percentage (:covered-in-past-6-months statistics)
                                (:territories statistics))]
         [:li {}
          (i18n/t "Statistics.coveredInPast12Months") ": "
          (count-and-percentage (:covered-in-past-12-months statistics)
                                (:territories statistics))]
         (when-some [days (:average-assignment-days statistics)]
           [:li {}
            (-> (i18n/t "Statistics.averageAssignmentDurationDays")
                (str/replace "{{days}}" (str days)))])])))))


(defn- checklist-item-status [completed?]
  (let [styles (:CongregationPage (css/modules))]
    (if completed?
      {:class (:completed styles)
       :data-test-icon "✅"}
      {:data-test-icon "⏳"})))

(defn getting-started [{:keys [getting-started permissions]}]
  (when (:view-getting-started permissions)
    (let [styles (:CongregationPage (css/modules))]
      (info-box/view
       {:title (i18n/t "GettingStarted.title")}
       (h/html
        [:ol {:class (:checklist styles)}
         [:li (checklist-item-status (:congregation-boundary? getting-started))
          [:a {:href "/documentation#how-to-create-congregation-boundaries"}
           (i18n/t "GettingStarted.congregationBoundary")]]
         [:li (checklist-item-status (:territories? getting-started))
          [:a {:href "/documentation#how-to-create-and-edit-territories"}
           (i18n/t "GettingStarted.territories")]]
         [:li (checklist-item-status (:publishers? getting-started))
          [:a {:href (str html/*page-path* "/settings#publishers-section")}
           (i18n/t "GettingStarted.publishers")]]
         [:li (checklist-item-status (:territories-assigned? getting-started))
          [:a {:href (str html/*page-path* "/territories")}
           (i18n/t "GettingStarted.territoriesAssigned")]]
         [:li (checklist-item-status (:share-link-created? getting-started))
          [:a {:href (str html/*page-path* "/territories")}
           (i18n/t "GettingStarted.shareLinkCreated")]]
         [:li (checklist-item-status (:qr-code-scanned? getting-started))
          [:a {:href (str html/*page-path* "/printouts")}
           (i18n/t "GettingStarted.qrCodeScanned")]]]
        [:p {} (-> (i18n/t "SupportPage.mailingListAd")
                   (str/replace "<0>" "<a href=\"https://groups.google.com/g/territory-bro-announcements\" target=\"_blank\">")
                   (str/replace "</0>" "</a>")
                   (h/raw))])))))


(defn view [{:keys [congregation permissions] :as model}]
  (let [styles (:CongregationPage (css/modules))]
    (h/html
     [:h1 {} (:congregation/name congregation)]
     [:div.pure-g
      [:div.pure-u-1.pure-u-md-1-2
       [:ul.home-navigation {:class (:navigation styles)}
        [:li [:a {:href (str html/*page-path* "/territories")}
              [:span {:aria-hidden ""} "📍"]
              " "
              (i18n/t "TerritoryListPage.title")]]
        (when (:view-printouts-page permissions)
          [:li [:a {:href (str html/*page-path* "/printouts")}
                [:span {:aria-hidden ""} "🖨️"]
                " "
                (i18n/t "PrintoutPage.title")]])
        (when (:view-settings-page permissions)
          [:li [:a {:href (str html/*page-path* "/settings")}
                [:span {:aria-hidden ""} "⚙️"]
                " "
                (i18n/t "SettingsPage.title")]])]]
      [:div.pure-u-1.pure-u-md-1-2 {}
       (statistics model)
       (getting-started model)]])))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/congregation/:congregation"
   {:middleware [dmz/wrap-db-connection]
    :get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}}])
