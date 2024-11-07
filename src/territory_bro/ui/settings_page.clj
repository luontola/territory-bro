(ns territory-bro.ui.settings-page
  (:require [clojure.string :as str]
            [ring.util.codec :as codec]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.util :as util]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.layout :as layout])
  (:import (territory_bro ValidationException)))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        publisher-id (get-in request [:path-params :publisher])
        _ (when-not (dmz/view-settings-page? cong-id)
            (dmz/access-denied!))
        congregation (dmz/get-congregation cong-id)
        publisher (dmz/get-publisher cong-id publisher-id)
        publishers (dmz/list-publishers cong-id)
        territories-by-assigned-publisher (group-by #(-> % :territory/current-assignment :publisher/id)
                                                    (dmz/list-territories cong-id))
        enrich-publisher-with-assignments (fn [publisher]
                                            (if-some [territories (get territories-by-assigned-publisher (:publisher/id publisher))]
                                              (let [territories (mapv #(select-keys % [:territory/id :territory/number])
                                                                      territories)]
                                                (assoc publisher :assigned-territories territories))
                                              publisher))
        users (dmz/list-congregation-users cong-id)
        new-user (some-> (get-in request [:params :new-user])
                         (parse-uuid))]
    {:congregation (select-keys congregation [:congregation/id :congregation/name])
     :publisher (some-> publisher enrich-publisher-with-assignments) ; nil, except when editing a publisher
     :publishers (map enrich-publisher-with-assignments publishers) ; lazy, because not every htmx component uses publishers
     :users (->> users
                 (mapv (fn [user]
                         (assoc user :new? (= new-user (:user/id user)))))
                 (sort-by (fn [user]
                            ;; new user first, then alphabetically by name
                            [(if (:new? user) 1 2)
                             (-> user :user/attributes :name str str/lower-case)])))
     :permissions {:configure-congregation (dmz/allowed? [:configure-congregation cong-id])
                   :gis-access (dmz/allowed? [:gis-access cong-id])}
     :form {:congregation-name (or (get-in request [:params :congregation-name])
                                   (:congregation/name congregation))
            :loans-csv-url (or (get-in request [:params :loans-csv-url])
                               (:congregation/loans-csv-url congregation))
            :publisher-id publisher-id
            :publisher-name (or (get-in request [:params :publisher-name])
                                (:publisher/name publisher)
                                "")
            :user-id (get-in request [:params :user-id])}}))


;;;; Congregation settings

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
       [:a {:href "/support"}
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

(defn congregation-settings-section [{:keys [permissions form errors]}]
  (when (:configure-congregation permissions)
    (let [styles (:CongregationSettings (css/modules))
          errors (group-by first errors)]
      (h/html
       [:section
        [:form.pure-form.pure-form-aligned {:method "post"}
         (html/anti-forgery-field)
         [:fieldset
          (let [error? (contains? errors :missing-name)]
            [:div.pure-control-group
             [:label {:for "congregation-name"}
              (i18n/t "CongregationSettings.congregationName")]
             [:input#congregation-name {:name "congregation-name"
                                        :value (:congregation-name form)
                                        :type "text"
                                        :required true
                                        :aria-invalid (when error? "true")}]
             (when error?
               " ⚠️ ")])

          [:details {:class (:experimentalFeatures styles)
                     :open (not (str/blank? (:loans-csv-url form)))}
           [:summary (i18n/t "CongregationSettings.experimentalFeatures")]

           [:div {:lang "en"}
            (let [error? (contains? errors :disallowed-loans-csv-url)]
              [:div.pure-control-group
               [:label {:for "loans-csv-url"}
                "Territory loans CSV URL (optional)"]
               [:input#loans-csv-url {:name "loans-csv-url"
                                      :value (:loans-csv-url form)
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

(declare view)
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


;;;; Editing maps

(defn editing-maps-section [{:keys [permissions]}]
  (when (:gis-access permissions)
    (h/html
     [:section
      [:h2 (i18n/t "EditingMaps.title")]
      [:p (-> (i18n/t "EditingMaps.introduction")
              (str/replace "<0>" "<a href=\"/documentation\">")
              (str/replace "</0>" "</a>")
              (str/replace "<1>" "<a href=\"https://www.qgis.org/\" target=\"_blank\">")
              (str/replace "</1>" "</a>")
              (h/raw))]
      [:p [:a.pure-button {:href (str html/*page-path* "/qgis-project")}
           (i18n/t "EditingMaps.downloadQgisProject")]]])))

(defn download-qgis-project [request]
  (let [cong-id (get-in request [:path-params :congregation])
        {:keys [content filename]} (dmz/download-qgis-project cong-id)]
    (-> (http-response/ok content)
        (response/content-type "application/octet-stream")
        (response/header "Content-Disposition" (str "attachment; filename=\"" filename "\"")))))


;;;; Publishers

(defn view-publisher-row [{:keys [publisher congregation permissions]}]
  (when (:configure-congregation permissions)
    (if (nil? publisher)
      ""
      (h/html
       [:tr {:hx-target "this"
             :hx-swap "outerHTML"}
        [:td (:publisher/name publisher)]
        [:td (->> (:assigned-territories publisher)
                  (util/natural-sort-by :territory/number)
                  (mapv (fn [territory]
                          (h/html [:a {:href (str "/congregation/" (:congregation/id congregation) "/territories/" (:territory/id territory))}
                                   (:territory/number territory)])))
                  (interpose ", "))]
        [:td {:style {:text-align "right"}}
         [:a {:hx-get (str html/*page-path* "/publishers/" (:publisher/id publisher) "/edit")
              :href "#"}
          "Edit"]]])))) ; TODO: i18n

(def ^:private publisher-table-column-count 3)

(defn edit-publisher-row [{:keys [publisher form permissions]}]
  (when (:configure-congregation permissions)
    (if (nil? publisher)
      ""
      (let [styles (:UserManagement (css/modules)) ; TODO: css module for publisher management
            publisher-id (:publisher-id form)
            errors nil ; TODO: test through form submit
            missing-name? (contains? errors :missing-name)
            non-unique-name? (contains? errors :non-unique-name)
            error? (or missing-name?
                       non-unique-name?)]
        (h/html
         [:tr {:hx-target "this"
               :hx-swap "outerHTML"}
          [:td {:colspan publisher-table-column-count
                :style {:padding "4px"}}
           [:form.pure-form {:hx-post (str html/*page-path* "/publishers/" publisher-id)}
            [:input#publisher-name {:name "publisher-name"
                                    :type "text"
                                    :autocomplete "off"
                                    :data-1p-ignore true ; don't offer to fill with 1Password https://developer.1password.com/docs/web/compatible-website-design/
                                    :required true
                                    :value (:publisher-name form)
                                    :aria-invalid (when error? "true")}]
            " "
            [:button.pure-button.pure-button-primary {:type "submit"}
             "Save"]
            " "
            [:button.pure-button {:hx-delete (str html/*page-path* "/publishers/" publisher-id)
                                  :type "button"
                                  :class (:removeUser styles)}
             "Delete"]
            " "
            [:button.pure-button {:hx-get (str html/*page-path* "/publishers/" publisher-id)
                                  :type "button"}
             "Cancel"]]]])))))

(defn add-publisher-row [{:keys [form]}]
  (let [errors nil
        missing-name? (contains? errors :missing-name)
        non-unique-name? (contains? errors :non-unique-name)
        error? (or missing-name?
                   non-unique-name?)]
    (h/html
     [:tr
      [:td {:colspan publisher-table-column-count
            :style {:padding "4px"}}
       [:form.pure-form {:hx-post (str html/*page-path* "/publishers")}
        [:input#publisher-name {:name "publisher-name"
                                :type "text"
                                :autocomplete "off"
                                :data-1p-ignore true ; don't offer to fill with 1Password https://developer.1password.com/docs/web/compatible-website-design/
                                :required true
                                :value (:publisher-name form)
                                :aria-invalid (when error? "true")}]
        " "
        [:button.pure-button.pure-button-primary {:type "submit"}
         "Add publisher"] ; TODO: i18n
        [:div
         (when error?
           [:span.pure-form-message-inline " ⚠️ "])
         (when non-unique-name?
           [:span.pure-form-message-inline "There is already a publisher with that name"])]]]]))) ; TODO: i18n

(defn publisher-management-section [{:keys [publishers permissions] :as model}]
  (when (:configure-congregation permissions)
    (h/html
     [:section#publishers-section {:hx-target "this"
                                   :hx-swap "outerHTML"}
      [:h2 "Publishers"] ; TODO: i18n
      [:table.pure-table.pure-table-horizontal
       [:thead
        [:tr
         [:th "Name"] ; TODO: i18n
         [:th "Assigned territories"] ; TODO: i18n
         [:th]]]
       [:tbody
        (for [publisher (util/natural-sort-by :publisher/name publishers)]
          (view-publisher-row (assoc model :publisher publisher)))
        (add-publisher-row model)]]])))


;;;; Users

(defn identity-provider [user]
  (let [sub (str (:user/subject user))]
    (cond
      (str/starts-with? sub "auth0|") "Auth0"
      (str/starts-with? sub "google-oauth2|") "Google"
      (str/starts-with? sub "facebook|") "Facebook"
      :else sub)))

(defn users-table-row [user {:keys [congregation]}]
  (let [styles (:UserManagement (css/modules))
        current-user? (= (:user/id user)
                         (:user/id auth/*user*))
        {:keys [name email email_verified picture]} (:user/attributes user)]
    (h/html
     [:tr {:class (when (:new? user)
                    (:newUser styles))}
      [:td {:class (:profilePicture styles)}
       (when (some? picture)
         [:img {:src picture
                :alt ""}])]
      [:td
       (if (str/blank? name)
         (:user/id user)
         name)
       (when current-user?
         (h/html " " [:em "(" (i18n/t "UserManagement.you") ")"]))]
      [:td
       email
       (when (and (some? email)
                  (not email_verified))
         (h/html " " [:em "(" (i18n/t "UserManagement.unverified") ")"]))]
      [:td (identity-provider user)]
      [:td [:button.pure-button {:type "button"
                                 :class (:removeUser styles)
                                 :hx-delete (str html/*page-path* "/users?user-id=" (codec/url-encode (:user/id user)))
                                 :hx-confirm (when current-user?
                                               (-> (i18n/t "UserManagement.removeYourselfWarning")
                                                   (str/replace "{{congregation}}" (:congregation/name congregation))))}
            (i18n/t "UserManagement.removeUser")]]])))

(defn user-management-section [{:keys [users permissions form errors] :as model}]
  (when (:configure-congregation permissions)
    (let [errors (group-by first errors)]
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
                              :value (:user-id form)
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
          (for [user users]
            (users-table-row user model))]]]))))

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


;;;; Main view

(defn view [model]
  (let [styles (:SettingsPage (css/modules))]
    (h/html
     [:h1 (i18n/t "SettingsPage.title")]
     [:div {:class (:sections styles)}
      (congregation-settings-section model)
      (editing-maps-section model)
      (publisher-management-section model)
      (user-management-section model)])))

(def routes
  ["/congregation/:congregation/settings"
   {:middleware [[html/wrap-page-path ::page]
                 [dmz/wrap-access-check dmz/view-settings-page?]
                 dmz/wrap-db-connection]}
   [""
    {:name ::page
     :get {:handler (fn [request]
                      (-> (model! request)
                          (view)
                          (layout/page! request)
                          (html/response)))}
     :post {:handler (fn [request]
                       (save-congregation-settings! request))}}]

   ["/qgis-project"
    {:get {:handler (fn [request]
                      (download-qgis-project request))}}]

   ["/publishers"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (publisher-management-section)
                          (html/response)))}
     :post {:handler (fn [request]
                       ; TODO: add publisher
                       (http-response/see-other (str html/*page-path* "/publishers")))}}]
   ["/publishers/:publisher"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (view-publisher-row)
                          (html/response)))}
     :post {:handler (fn [request]
                       ; TODO: save publisher
                       (http-response/see-other (:uri request)))}
     :delete {:handler (fn [request]
                         ; TODO: remove publisher
                         (http-response/see-other (:uri request)))}}]
   ["/publishers/:publisher/edit"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (edit-publisher-row)
                          (html/response)))}}]

   ["/users"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (user-management-section)
                          (html/response)))}
     :post {:handler (fn [request]
                       (add-user! request))}
     :delete {:handler (fn [request]
                         (remove-user! request))}}]])
