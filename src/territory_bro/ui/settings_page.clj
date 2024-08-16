;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.settings-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [ring.util.codec :as codec]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout])
  (:import (territory_bro ValidationException)))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        congregation (if (= "demo" cong-id) ; TODO: replace with permission check for editing settings
                       (http-response/not-found! "Not available in demo")
                       (dmz/get-congregation cong-id))
        users (dmz/list-congregation-users cong-id)
        new-user (some-> (get-in request [:params :new-user])
                         (parse-uuid))]
    {:congregation/name (or (get-in request [:params :congregation-name])
                            (:congregation/name congregation))
     :congregation/loans-csv-url (or (get-in request [:params :loans-csv-url])
                                     (:congregation/loans-csv-url congregation))
     :users (->> users
                 (mapv (fn [user]
                         (assoc user :new? (= new-user (:id user)))))
                 (sort-by (fn [user]
                            ;; new user first, then alphabetically by name
                            [(if (:new? user) 1 2)
                             (str/lower-case (str (:name user)))])))
     :congregation/permissions (select-keys (:congregation/permissions congregation) [:configure-congregation :gis-access])
     :form/user-id (get-in request [:params :user-id])}))


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
  (when (-> model :congregation/permissions :configure-congregation)
    (let [styles (:CongregationSettings (css/modules))
          errors (->> (:errors model)
                      (group-by first))]
      (h/html
       [:section
        [:form.pure-form.pure-form-aligned {:method "post"}
         [:fieldset
          (let [error? (contains? errors :missing-name)]
            [:div.pure-control-group
             [:label {:for "congregation-name"}
              (i18n/t "CongregationSettings.congregationName")]
             [:input#congregation-name {:name "congregation-name"
                                        :value (:congregation/name model)
                                        :type "text"
                                        :required true
                                        :aria-invalid (when error? "true")}]
             (when error?
               " ⚠️ ")])

          [:details {:class (:experimentalFeatures styles)
                     :open (not (str/blank? (:congregation/loans-csv-url model)))}
           [:summary (i18n/t "CongregationSettings.experimentalFeatures")]

           [:div {:lang "en"}
            (let [error? (contains? errors :disallowed-loans-csv-url)]
              [:div.pure-control-group
               [:label {:for "loans-csv-url"}
                "Territory loans CSV URL (optional)"]
               [:input#loans-csv-url {:name "loans-csv-url"
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
  (when (-> model :congregation/permissions :gis-access)
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

(defn users-table-row [user model]
  (let [styles (:UserManagement (css/modules))
        current-user? (= (:id user)
                         (:user/id auth/*user*))]
    (h/html
     [:tr {:class (when (:new? user)
                    (:newUser styles))}
      [:td {:class (:profilePicture styles)}
       (when (some? (:picture user))
         [:img {:src (:picture user)
                :alt ""}])]
      [:td
       (if (str/blank? (:name user))
         (:id user)
         (:name user))
       (when current-user?
         (h/html " " [:em "(" (i18n/t "UserManagement.you") ")"]))]
      [:td
       (:email user)
       (when (and (some? (:email user))
                  (not (:emailVerified user)))
         (h/html " " [:em "(" (i18n/t "UserManagement.unverified") ")"]))]
      [:td (identity-provider user)]
      [:td [:button.pure-button {:type "button"
                                 :class (:removeUser styles)
                                 :hx-delete (str html/*page-path* "/users?user-id=" (codec/url-encode (:id user)))
                                 :hx-confirm (when current-user?
                                               (-> (i18n/t "UserManagement.removeYourselfWarning")
                                                   (str/replace "{{congregation}}" (:congregation/name model))))}
            (i18n/t "UserManagement.removeUser")]]])))

(defn user-management-section [model]
  (when (-> model :congregation/permissions :configure-congregation)
    (let [errors (group-by first (:errors model))]
      (h/html
       [:section#users-section {:hx-target "this"
                                :hx-swap "outerHTML"}
        [:h2 (i18n/t "UserManagement.title")]
        [:p (-> (i18n/t "UserManagement.addUserInstructions")
                (str/replace "{{joinPageUrl}}" (str (:public-url config/env) "/join"))
                (str/replace "<0>" "<a href=\"/join\">")
                (str/replace "</0>" "</a>")
                (h/raw))]

        [:form.pure-form.pure-form-aligned {:hx-post (str html/*page-path* "/users")}
         [:fieldset
          (let [invalid-user-id? (contains? errors :invalid-user-id)
                no-such-user? (contains? errors :no-such-user)
                error? (or invalid-user-id?
                           no-such-user?)]
            [:div.pure-control-group
             [:label {:for "user-id"}
              (i18n/t "UserManagement.userId")]
             [:input#user-id {:name "user-id"
                              :type "text"
                              :autocomplete "off"
                              :data-1p-ignore true ; don't offer to fill with 1Password https://developer.1password.com/docs/web/compatible-website-design/
                              :required true
                              :pattern "\\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\s*"
                              :value (:form/user-id model)
                              :aria-invalid (when error? "true")}]
             (when error?
               " ⚠️ ")
             (when no-such-user?
               [:span.pure-form-message-inline (i18n/t "UserManagement.userIdNotExist")])])
          [:div.pure-controls
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
          (for [user (:users model)]
            (users-table-row user model))]]]))))


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
  (let [cong-id (get-in request [:path-params :congregation])
        name (get-in request [:params :congregation-name])
        loans-csv-url (get-in request [:params :loans-csv-url])]
    (try
      (dmz/dispatch! {:command/type :congregation.command/update-congregation
                      :congregation/id cong-id
                      :congregation/name name
                      :congregation/loans-csv-url loans-csv-url})
      (http-response/see-other html/*page-path*)
      (catch Exception e
        (forms/validation-error-page-response e request model! view)))))

(defn add-user! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        user-id (get-in request [:params :user-id])]
    (try
      (let [user-id (or (parse-uuid (str/trim (str user-id)))
                        (throw (ValidationException. [[:invalid-user-id]])))]
        (dmz/dispatch! {:command/type :congregation.command/add-user
                        :congregation/id cong-id
                        :user/id user-id})
        (http-response/see-other (str html/*page-path* "/users?new-user=" user-id)))
      (catch Exception e
        (forms/validation-error-htmx-response e request model! user-management-section)))))

(defn remove-user! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        user-id (parse-uuid (get-in request [:params :user-id]))]
    (dmz/dispatch! {:command/type :congregation.command/set-user-permissions
                    :congregation/id cong-id
                    :user/id user-id
                    :permission/ids []})
    (if (= user-id (auth/current-user-id))
      (-> (html/response "")
          ;; the user can no longer view this congregation, so redirect them to the front page
          (response/header "hx-redirect" "/"))
      (http-response/see-other (str html/*page-path* "/users")))))

(defn download-qgis-project [request]
  (let [cong-id (get-in request [:path-params :congregation])
        {:keys [content filename]} (dmz/download-qgis-project cong-id)]
    (-> (http-response/ok content)
        (response/content-type "application/octet-stream")
        (response/header "Content-Disposition" (str "attachment; filename=\"" filename "\"")))))

(def routes
  ["/congregation/:congregation/settings"
   {:middleware [[html/wrap-page-path ::page]]}
   [""
    {:name ::page
     :get {:middleware [dmz/wrap-db-connection]
           :handler (fn [request]
                      (-> (view! request)
                          (layout/page! request)
                          (html/response)))}
     :post {:middleware [dmz/wrap-db-connection]
            :handler (fn [request]
                       (save-congregation-settings! request))}}]

   ["/qgis-project"
    {:get {:handler (fn [request]
                      (download-qgis-project request))}}]

   ["/users"
    {:get {:middleware [dmz/wrap-db-connection]
           :handler (fn [request]
                      (-> (model! request)
                          (user-management-section)
                          (html/response)))}
     :post {:middleware [dmz/wrap-db-connection]
            :handler (fn [request]
                       (add-user! request))}
     :delete {:middleware [dmz/wrap-db-connection]
              :handler (fn [request]
                         (remove-user! request))}}]])
