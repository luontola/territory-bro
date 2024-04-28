;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.settings-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [ring.util.http-response :as http-response]
            [territory-bro.api :as api]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout])
  (:import (clojure.lang ExceptionInfo)))

(defn model! [request]
  (let [congregation (:body (api/get-congregation request {}))]
    {:congregation/name (or (get-in request [:params :congregationName])
                            (:name congregation))
     :congregation/loans-csv-url (or (get-in request [:params :loansCsvUrl])
                                     (:loansCsvUrl congregation))
     :congregation/users (:users congregation)
     :permissions (select-keys (:permissions congregation) [:configureCongregation :gisAccess])}))


(defn loans-csv-url-info []
  (let [styles (:CongregationSettings (css/modules))]
    (info-box/view
     {:title "Early Access Feature: Integrate with territory loans data from Google Sheets"}
     (h/html
      [:p "If you keep track of your territory loans using Google Sheets, it's possible to export the data from there and "
       "visualize it on the map on Territory Bro's Territories page. Eventually Territory Bro will handle the territory "
       "loans accounting all by itself, but in the meanwhile this workaround gives some of the benefits."]
      [:p "Here is an "
       [:a {:href "https://docs.google.com/spreadsheets/d/1pa_EIyuCpWGbEOXFOqjc7P0XfDWbZNRKIKXKLpnKkx4/edit?usp=sharing"
            :target "_blank"}
        "example spreadsheet"]
       " that you can use as a starting point. Also please "
       [:a {:href (str html/*page-path* "/../support")}
        "contact me"]
       " for assistance and so that I will know to help you later with migration to full accounting support."]
      [:p "You'll need to create a sheet with the following structure:"]
      [:table {:class (:spreadsheet styles)}
       [:tbody
        [:tr [:td "Number"] [:td "Loaned"] [:td "Staleness"]]
        [:tr [:td "101"] [:td "TRUE"] [:td "2"]]
        [:tr [:td "102"] [:td "FALSE"] [:td "6"]]]]
      [:p "The " [:i "Number"] " column should contain the territory number. It's should match the territories in Territory Bro."]
      [:p "The " [:i "Loaned"] " column should contain \"TRUE\" when the territory is currently loaned to a publisher and \"FALSE\" when nobody has it."]
      [:p "The " [:i "Staleness"] " column should indicate the number of months since the territory was last loaned or returned."]
      [:p "The first row of the sheet must contain the column names, but otherwise the sheet's structure is flexible: "
       "The columns can be in any order. Columns with other names are ignored. Empty rows are ignored."]
      [:p "After you have such a sheet, you can expose it to the Internet through " [:tt "File | Share | Publish to web"] ". "
       "Publish that sheet as a CSV file and enter its URL to the above field on this settings page."]))))

(defn congregation-settings-section [model]
  (when (-> model :permissions :configureCongregation)
    (let [styles (:CongregationSettings (css/modules))
          errors (->> (:errors model)
                      (group-by first))]
      (h/html
       [:section
        [:form.pure-form.pure-form-aligned {:method "post"}
         [:fieldset
          (let [error? (contains? errors :missing-name)]
            [:div.pure-control-group
             [:label {:for "congregationName"}
              (i18n/t "CongregationSettings.congregationName")]
             [:input#congregationName {:name "congregationName"
                                       :value (:congregation/name model)
                                       :type "text"
                                       :required true
                                       :aria-invalid (when error? "true")}]
             (when error?
               " ⚠️ ")])

          [:details {:class (:experimentalFeatures styles)
                     :open (some? (:congregation/loans-csv-url model))}
           [:summary (i18n/t "CongregationSettings.experimentalFeatures")]

           [:div {:lang "en"}
            (let [error? (contains? errors :disallowed-loans-csv-url)]
              [:div.pure-control-group
               [:label {:for "loansCsvUrl"}
                "Territory loans CSV URL (optional)"]
               [:input#loansCsvUrl {:name "loansCsvUrl"
                                    :value (:congregation/loans-csv-url model)
                                    :type "text"
                                    :size "50"
                                    :pattern "https://docs\\.google\\.com/.*"
                                    :aria-invalid (when error? "true")}]
               (when error?
                 " ⚠️ ")
               [:span.pure-form-message-inline "Link to a Google Sheets spreadsheet published to the web as CSV"]])
            (loans-csv-url-info)]]

          [:div.pure-controls
           [:button.pure-button.pure-button-primary {:type "submit"}
            (i18n/t "CongregationSettings.save")]]]]]))))


(defn editing-maps-section [model]
  (when (-> model :permissions :gisAccess)
    (h/html
     [:section
      [:h2 (i18n/t "EditingMaps.title")]
      [:p (-> (i18n/t "EditingMaps.introduction")
              (str/replace "<0>" "<a href=\"https://territorybro.com/guide/\" target=\"_blank\">")
              (str/replace "</0>" "</a>")
              (str/replace "<1>" "<a href=\"https://www.qgis.org/\" target=\"_blank\">")
              (str/replace "</1>" "</a>")
              (h/raw))]
      [:p [:a.pure-button {:href (str html/*page-path* "/qgis-project")}
           (i18n/t "EditingMaps.downloadQgisProject")]]])))


(defn identity-provider [user]
  (let [sub (or (:sub user) "")]
    (cond
      (str/starts-with? sub "google-oauth2|") "Google"
      (str/starts-with? sub "facebook|") "Facebook"
      :else sub)))

(defn users-table-row [user]
  (let [styles (:UserManagement (css/modules))]
    (h/html
     [:tr
      [:td {:class (:profilePicture styles)}
       (when (some? (:picture user))
         [:img {:src (:picture user)
                :alt ""}])]
      [:td
       (if (str/blank? (:name user))
         (:id user)
         (:name user))
       (when (= (:id user)
                (:user/id auth/*user*))
         (h/html " " [:em "(" (i18n/t "UserManagement.you") ")"]))]
      [:td
       (:email user)
       (when (and (some? (:email user))
                  (not (:emailVerified user)))
         (h/html " " [:em "(" (i18n/t "UserManagement.unverified") ")"]))]
      [:td (identity-provider user)]
      ;; TODO: implement removing
      [:td [:button.pure-button {:type "button"
                                 :class (:removeUser styles)}
            (i18n/t "UserManagement.removeUser")]]])))

(defn user-management-section [model]
  (when (-> model :permissions :configureCongregation)
    (h/html
     [:section#users-section
      [:h2 (i18n/t "UserManagement.title")]
      [:p (-> (i18n/t "UserManagement.addUserInstructions")
              (str/replace "{{joinPageUrl}}" "http://localhost:8080/join")
              (str/replace "<0>" "<a href=\"/join\">")
              (str/replace "</0>" "</a>")
              (h/raw))]

      [:form.pure-form.pure-form-aligned
       [:fieldset
        [:div.pure-control-group
         [:label {:for "user-id"}
          (i18n/t "UserManagement.userId")]
         [:input#user-id {:name "userId"
                          :type "text"
                          :autocomplete "off"
                          :required true
                          :pattern "\\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\s*"
                          :value ""}]]
        [:div.pure-controls
         ;; TODO: implement adding
         [:button.pure-button.pure-button-primary {:type "submit"}
          (i18n/t "UserManagement.addUser")]]]]

      [:table.pure-table.pure-table-horizontal
       [:thead
        [:tr
         [:th]
         [:th (i18n/t "UserManagement.name")]
         [:th (i18n/t "UserManagement.email")]
         [:th (i18n/t "UserManagement.loginMethod")]
         [:th (i18n/t "UserManagement.actions")]]]
       [:tbody
        (for [user (:congregation/users model)]
          (users-table-row user))]]])))


(defn view [model]
  (let [styles (:SettingsPage (css/modules))]
    (h/html
     [:h1 (i18n/t "SettingsPage.title")]
     [:div {:class (:sections styles)}
      (congregation-settings-section model)
      (editing-maps-section model)
      (user-management-section model)])))

(defn view! [request]
  (view (model! request)))

(defn save-congregation-settings! [request]
  (try
    (api/save-congregation-settings request)
    (http-response/see-other html/*page-path*)
    (catch ExceptionInfo e ; TODO: catch ValidationException directly
      (let [errors (when (and (= :ring.util.http-response/response (:type (ex-data e)))
                              (= 400 (:status (:response (ex-data e)))))
                     (:errors (:body (:response (ex-data e)))))]
        (if (some? errors)
          (-> (model! request)
              (assoc :errors errors)
              (view)
              (layout/page! request)
              (html/response)
              (assoc :status 400))
          (throw e))))))

(def routes
  ["/congregation/:congregation/settings"
   {:middleware [[html/wrap-page-path ::page]]}
   [""
    {:name ::page
     :get {:handler (fn [request]
                      (-> (view! request)
                          (layout/page! request)
                          (html/response)))}
     :post {:handler (fn [request]
                       (save-congregation-settings! request))}}]

   ["/qgis-project"
    {:get {:handler (fn [request]
                      (api/download-qgis-project request))}}]])
