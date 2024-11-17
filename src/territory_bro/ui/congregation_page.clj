(ns territory-bro.ui.congregation-page
  (:require [clojure.string :as str]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout]))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        congregation (dmz/get-congregation cong-id)]
    {:congregation (select-keys congregation [:congregation/name])
     :statistics {:congregation-boundary? (some? (dmz/get-congregation-boundary cong-id))
                  :territories (count (dmz/list-territories cong-id))}
     :permissions {:view-printouts-page (dmz/view-printouts-page? cong-id)
                   :view-settings-page (dmz/view-settings-page? cong-id)
                   :gis-access (dmz/allowed? [:gis-access cong-id])}}))

(defn- checklist-item-status [completed?]
  (let [styles (:CongregationPage (css/modules))]
    (if completed?
      {:class (:completed styles)
       :data-test-icon "✅"}
      {:data-test-icon "⏳"})))

(defn getting-started [{:keys [statistics permissions]}]
  (when (:gis-access permissions)
    (let [styles (:CongregationPage (css/modules))
          content (h/html
                   [:ol {:class (:checklist styles)}
                    [:li (checklist-item-status (:congregation-boundary? statistics))
                     [:a {:href "/documentation#how-to-create-congregation-boundaries"}
                      (i18n/t "GettingStarted.congregationBoundary")]]
                    [:li (checklist-item-status (pos? (:territories statistics)))
                     [:a {:href "/documentation#how-to-create-and-edit-territories"}
                      (i18n/t "GettingStarted.territories")]]]
                   [:p (-> (i18n/t "SupportPage.mailingListAd")
                           (str/replace "<0>" "<a href=\"https://groups.google.com/g/territory-bro-announcements\" target=\"_blank\">")
                           (str/replace "</0>" "</a>")
                           (h/raw))])]
      (h/html
       [:aside {:class (:getting-started styles)}
        (info-box/view
         {:title (i18n/t "GettingStarted.title")}
         content)]))))

(defn view [{:keys [congregation permissions] :as model}]
  (let [styles (:CongregationPage (css/modules))]
    (h/html
     [:h1 (:congregation/name congregation)]
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
      [:div.pure-u-1.pure-u-md-1-2
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
