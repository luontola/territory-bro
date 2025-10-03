(ns territory-bro.ui.settings-page
  (:require [clojure.string :as str]
            [ring.util.codec :as codec]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.gis.qgis :as qgis]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.util :as util]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.export :as export]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout])
  (:import (java.net URLEncoder)
           (java.nio.charset StandardCharsets)
           (java.text Normalizer Normalizer$Form)
           (java.time ZoneId ZoneOffset)
           (java.time.format DateTimeFormatter)
           (territory_bro ValidationException)))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        publisher-id (get-in request [:path-params :publisher])
        _ (when-not (dmz/view-settings-page? cong-id)
            (dmz/access-denied!))
        congregation (dmz/get-congregation cong-id)

        publisher (dmz/get-publisher cong-id publisher-id)
        publishers (dmz/list-publishers cong-id)
        new-publisher (some-> (get-in request [:params :new-publisher])
                              parse-uuid)
        territories-by-assigned-publisher (group-by #(-> % :territory/current-assignment :publisher/id)
                                                    (dmz/list-territories cong-id))
        enrich-publisher (fn [publisher]
                           (-> publisher
                               (dissoc :congregation/id)
                               (assoc :assigned-territories (->> (get territories-by-assigned-publisher (:publisher/id publisher))
                                                                 (mapv #(select-keys % [:territory/id :territory/number]))))
                               (assoc :new? (= new-publisher (:publisher/id publisher)))))

        users (dmz/list-congregation-users cong-id)
        new-user (some-> (get-in request [:params :new-user])
                         parse-uuid)]
    {:congregation (select-keys congregation [:congregation/id :congregation/name :congregation/timezone])
     :publisher (some-> publisher enrich-publisher) ; nil, except when editing a publisher
     :publishers (map enrich-publisher publishers) ; lazy, because not every htmx component uses publishers
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
            :publisher-name (or (get-in request [:params :publisher-name])
                                (:publisher/name publisher)
                                "")
            :user-id (get-in request [:params :user-id])}}))


;;;; Congregation settings

(def local-date-time (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm"))

(defn congregation-timezone-field [{:keys [congregation]}]
  (let [styles (:CongregationSettings (css/modules))
        ^ZoneId timezone (:congregation/timezone congregation)]
    (h/html
     [:div.pure-control-group
      [:label {:for "congregation-timezone"}
       (i18n/t "CongregationSettings.timezone")]
      [:span#congregation-timezone {:style {:display "inline-block"
                                            :vertical-align "middle"}}
       (if (= ZoneOffset/UTC timezone)
         "UTC"
         (.getId timezone))
       " "
       [:span {:class (:current-time styles)}
        "("
        (-> (congregation/local-time congregation)
            (.format local-date-time))
        ")"]
       " "]
      [:span.pure-form-message-inline
       (if (= ZoneOffset/UTC timezone)
         (i18n/t "CongregationSettings.timezoneNotDefined")
         (i18n/t "CongregationSettings.timezoneDefined"))]])))

(defn congregation-settings-section [{:keys [permissions form errors] :as model}]
  (when (:configure-congregation permissions)
    (let [errors (group-by first errors)]
      (h/html
       [:section
        [:form.pure-form.pure-form-aligned {:method "post"}
         (html/anti-forgery-field)
         [:fieldset
          (let [error? (contains? errors :missing-name)]
            [:div.pure-control-group
             [:label {:for "congregation-name"}
              (i18n/t "CongregationSettings.congregationName")]
             [:input#congregation-name {:type "text"
                                        :name "congregation-name"
                                        :value (:congregation-name form)
                                        :required true
                                        :aria-invalid (when error? "true")}]
             (when error?
               " ⚠️ ")])

          (congregation-timezone-field model)

          [:div.pure-controls {:style {:margin-top ".75em"
                                       :margin-bottom ".5em"}}
           [:label.pure-checkbox
            [:input {:name "expire-shared-links-on-return"
                     :type "checkbox"
                     :value "true"
                     :checked true ; TODO: read current value from form
                     :style {:width "1.25rem"
                             :height "1.25rem"}}]
            " "
            (i18n/t "CongregationSettings.expireSharedLinksOnReturn")]]

          [:div.pure-controls
           [:button.pure-button.pure-button-primary {:type "submit"}
            (i18n/t "CongregationSettings.save")]]]]]))))

(declare view)
(defn save-congregation-settings! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        name (get-in request [:params :congregation-name])]
    (try
      (dmz/dispatch! {:command/type :congregation.command/update-congregation
                      :congregation/id cong-id
                      :congregation/name name})
      (http-response/see-other html/*page-path*)
      (catch Exception e
        (forms/validation-error-page-response e request model! view)))))


;;;; Editing maps

(defn editing-maps-section [{:keys [permissions]}]
  (when (:gis-access permissions)
    (h/html
     [:section
      [:h3 {} (i18n/t "EditingMaps.title")]
      [:p {} (-> (i18n/t "EditingMaps.introduction")
                 (str/replace "<0>" "<a href=\"/documentation\">")
                 (str/replace "</0>" "</a>")
                 (str/replace "<1>" "<a href=\"https://www.qgis.org/\" target=\"_blank\">")
                 (str/replace "</1>" "</a>")
                 (h/raw))]
      [:p [:a.pure-button {:href (str html/*page-path* "/qgis-project")}
           (i18n/t "EditingMaps.downloadQgisProject")]]])))

(defn territories-section [{:keys [permissions] :as model}]
  (when (or (:configure-congregation permissions)
            (:gis-access permissions))
    (h/html
     [:section
      [:h2 {} (i18n/t "TerritoriesSection.title")]
      (when (:configure-congregation permissions)
        [:p [:a.pure-button {:href (str html/*page-path* "/export-territories")}
             (i18n/t "TerritoriesSection.exportTerritoriesToExcel")]])
      (editing-maps-section model)])))

(defn sanitize-filename [basename extension]
  (let [basename (-> basename
                     (str/replace #"[<>:\"/\\|?*]" "") ; not allowed in Windows file names
                     (str/replace #"^[\.\s]+" "") ; leading comma = hidden file
                     (str/replace #"\s+" " ")
                     (str/trim))
        basename (if (str/blank? basename)
                   "file"
                   basename)]
    (str basename extension)))

(defn- remove-diacritics [^String s]
  (-> (Normalizer/normalize s Normalizer$Form/NFD) ; decompose characters
      (str/replace #"\p{M}" ""))) ; remove diacritics

(defn- to-ascii [^String s]
  (-> (.getBytes s StandardCharsets/US_ASCII) ; replaces non-ASCII characters with "?"
      (String. StandardCharsets/US_ASCII)
      (str/replace "?" "_"))) ; filenames can't contain "?", so replace it with "_"

(defn content-disposition [^String filename]
  (let [ascii-filename (-> filename remove-diacritics to-ascii)
        utf8-filename (-> (URLEncoder/encode filename StandardCharsets/UTF_8)
                          (str/replace "+" "%20"))]
    (str "attachment; filename=\"" ascii-filename "\"; filename*=UTF-8''" utf8-filename)))

(defn download-qgis-project [request]
  (let [cong-id (get-in request [:path-params :congregation])
        content (dmz/download-qgis-project cong-id)
        congregation (dmz/get-congregation cong-id)
        filename (sanitize-filename (:congregation/name congregation) qgis/qgis-project-ext)]
    (-> (http-response/ok content)
        (response/content-type "application/octet-stream")
        (response/header "Content-Disposition" (content-disposition filename)))))

(defn export-territories [request]
  (let [cong-id (get-in request [:path-params :congregation])
        content (export/export-territories cong-id)
        congregation (dmz/get-congregation cong-id)
        filename (sanitize-filename (:congregation/name congregation) export/excel-spreadsheet-ext)]
    (-> (http-response/ok content)
        (response/content-type "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        (response/header "Content-Disposition" (content-disposition filename)))))

;;;; Publishers

(defn view-publisher-row
  ([model]
   (view-publisher-row model nil))
  ([{:keys [publisher congregation permissions]} {:keys [autofocus]}]
   (when (:configure-congregation permissions)
     (if (nil? publisher)
       ""
       (let [styles (:SettingsPage (css/modules))]
         (h/html
          [:tr {:hx-target "this"
                :hx-swap "outerHTML"
                :class (when (:new? publisher)
                         (:new-row styles))}
           [:td {} (:publisher/name publisher)]
           [:td {} (->> (:assigned-territories publisher)
                        (util/natural-sort-by :territory/number)
                        (mapv (fn [territory]
                                (h/html [:a {:href (str "/congregation/" (:congregation/id congregation) "/territories/" (:territory/id territory))}
                                         (:territory/number territory)])))
                        (interpose ", "))]
           [:td {:class (:edit-button styles)}
            [:button.pure-button {:type "button"
                                  :hx-get (str html/*page-path* "/publishers/" (:publisher/id publisher) "/edit")
                                  :class (:edit-button styles)
                                  :autofocus autofocus}
             (i18n/t "PublisherManagement.edit")]]]))))))

(def ^:private publisher-table-column-count 3)

(defn- publisher-name-errors [{:keys [errors]}]
  (let [errors (group-by first errors)
        missing-name? (contains? errors :missing-name)
        non-unique-name? (contains? errors :non-unique-name)
        error? (or missing-name?
                   non-unique-name?)]
    (when error?
      (h/html
       [:div [:span.pure-form-message-inline
              " ⚠️ "
              (when missing-name?
                (i18n/t "PublisherManagement.missingNameError"))
              (when non-unique-name?
                (i18n/t "PublisherManagement.nonUniqueNameError"))]]))))

(defn- publisher-name-input [{:keys [form] :as model} {:keys [autofocus]}]
  (h/html
   [:input#publisher-name {:type "text"
                           :name "publisher-name"
                           :value (:publisher-name form)
                           :autofocus autofocus
                           :autocomplete "off" ; don't offer to fill with 1Password https://developer.1password.com/docs/web/compatible-website-design/
                           :data-1p-ignore true
                           :required true
                           :aria-label (i18n/t "PublisherManagement.publisherName")
                           :aria-invalid (when (some? (publisher-name-errors model))
                                           "true")}]))

(defn edit-publisher-row [{:keys [publisher permissions] :as model}]
  (when (:configure-congregation permissions)
    (if (nil? publisher)
      ""
      (let [styles (:SettingsPage (css/modules))
            publisher-id (:publisher/id publisher)]
        (h/html
         [:tr {:hx-target "this"
               :hx-swap "outerHTML"}
          [:td {:colspan publisher-table-column-count
                :class (:edit-publisher styles)}
           [:form.pure-form {:hx-post (str html/*page-path* "/publishers/" publisher-id)}
            (publisher-name-input model {:autofocus true})
            " "
            [:button.pure-button.pure-button-primary {:type "submit"}
             (i18n/t "PublisherManagement.save")]
            " "
            [:button.pure-button {:type "button"
                                  :hx-delete (str html/*page-path* "/publishers/" publisher-id)
                                  :hx-confirm (when-not (empty? (:assigned-territories publisher))
                                                (-> (i18n/t "PublisherManagement.deleteWarning")
                                                    (str/replace "{{name}}" (:publisher/name publisher))))
                                  :class (:delete-button styles)}
             (i18n/t "PublisherManagement.delete")]
            " "
            [:button.pure-button {:type "button"
                                  :hx-get (str html/*page-path* "/publishers/" publisher-id)}
             (i18n/t "PublisherManagement.cancel")]
            (publisher-name-errors model)]]])))))

(defn add-publisher-row [model]
  (let [styles (:SettingsPage (css/modules))]
    (h/html
     [:tr
      [:td {:colspan publisher-table-column-count
            :class (:edit-publisher styles)}
       [:form#add-publisher-form.pure-form {:hx-post (str html/*page-path* "/publishers")}
        (publisher-name-input model nil)
        " "
        [:button.pure-button.pure-button-primary {:type "submit"}
         (i18n/t "PublisherManagement.addPublisher")]
        (publisher-name-errors model)]]])))

(defn publisher-management-section [{:keys [publishers permissions] :as model}]
  (when (:configure-congregation permissions)
    (h/html
     [:section#publishers-section {:hx-target "this"
                                   :hx-swap "outerHTML"}
      [:h2 {} (i18n/t "PublisherManagement.title")]
      [:table.pure-table.pure-table-horizontal
       [:thead
        [:tr
         [:th {} (i18n/t "PublisherManagement.publisherName")]
         [:th {} (i18n/t "PublisherManagement.assignedTerritories")]
         [:th]]]
       [:tbody
        (for [publisher (util/natural-sort-by :publisher/name publishers)]
          (view-publisher-row (assoc model :publisher publisher)))
        (add-publisher-row model)]]])))

(defn add-publisher!
  ([request]
   (add-publisher! request (random-uuid)))
  ([request publisher-id]
   (let [cong-id (get-in request [:path-params :congregation])
         publisher-name (get-in request [:params :publisher-name])]
     (try
       (dmz/dispatch! {:command/type :publisher.command/add-publisher
                       :congregation/id cong-id
                       :publisher/id publisher-id
                       :publisher/name publisher-name})
       (http-response/see-other (str html/*page-path* "/publishers?new-publisher=" publisher-id))
       (catch Exception e
         (forms/validation-error-htmx-response e request model! publisher-management-section))))))

(defn update-publisher! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        publisher-id (get-in request [:path-params :publisher])
        publisher-name (get-in request [:params :publisher-name])]
    (try
      (dmz/dispatch! {:command/type :publisher.command/update-publisher
                      :congregation/id cong-id
                      :publisher/id publisher-id
                      :publisher/name publisher-name})
      (http-response/see-other (str html/*page-path* "/publishers/" publisher-id))
      (catch Exception e
        (forms/validation-error-htmx-response e request model! edit-publisher-row)))))

(defn delete-publisher! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        publisher-id (get-in request [:path-params :publisher])]
    (try
      (dmz/dispatch! {:command/type :publisher.command/delete-publisher
                      :congregation/id cong-id
                      :publisher/id publisher-id})
      (http-response/see-other (str html/*page-path* "/publishers/" publisher-id))
      (catch Exception e
        (forms/validation-error-htmx-response e request model! edit-publisher-row)))))


;;;; Users

(defn identity-provider [user]
  (let [sub (str (:user/subject user))]
    (cond
      (str/starts-with? sub "auth0|") "Auth0"
      (str/starts-with? sub "google-oauth2|") "Google"
      (str/starts-with? sub "facebook|") "Facebook"
      :else sub)))

(defn users-table-row [user {:keys [congregation]}]
  (let [styles (:SettingsPage (css/modules))
        current-user? (= (:user/id user)
                         (:user/id auth/*user*))
        {:keys [name email email_verified picture]} (:user/attributes user)]
    (h/html
     [:tr {:class (when (:new? user)
                    (:new-row styles))}
      [:td {:class (:profile-picture styles)}
       (when (some? picture)
         [:img {:src picture
                :alt ""}])]
      [:td {}
       (if (str/blank? name)
         (:user/id user)
         name)
       (when current-user?
         (h/html " " [:em "(" (i18n/t "UserManagement.you") ")"]))]
      [:td {}
       email
       (when (and (some? email)
                  (not email_verified))
         (h/html " " [:em "(" (i18n/t "UserManagement.unverified") ")"]))]
      [:td {}
       (identity-provider user)]
      [:td
       [:button.pure-button {:type "button"
                             :class (:delete-button styles)
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
        [:h2 {} (i18n/t "UserManagement.title")]
        [:p {} (-> (i18n/t "UserManagement.addUserInstructions")
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
             [:input#user-id {:type "text"
                              :name "user-id"
                              :value (:user-id form)
                              :autocomplete "off" ; don't offer to fill with 1Password https://developer.1password.com/docs/web/compatible-website-design/
                              :data-1p-ignore true
                              :required true
                              :pattern "\\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\s*"
                              :oninvalid (str "this.validity.patternMismatch && this.setCustomValidity(\"" (i18n/t "UserManagement.userIdWrongFormat") "\")")
                              :oninput "this.setCustomValidity('')"
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
           [:th {} (i18n/t "UserManagement.name")]
           [:th {} (i18n/t "UserManagement.email")]
           [:th {} (i18n/t "UserManagement.loginMethod")]
           [:th {} (i18n/t "UserManagement.actions")]]]
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
     [:h1 {} (i18n/t "SettingsPage.title")]
     [:div {:class (:sections styles)}
      (congregation-settings-section model)
      (territories-section model)
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

   ["/export-territories"
    {:get {:handler (fn [request]
                      (export-territories request))}}]

   ["/publishers"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (publisher-management-section)
                          (html/response)))}
     :post {:handler (fn [request]
                       (add-publisher! request))}}]
   ["/publishers/:publisher"
    {:get {:handler (fn [request]
                      (-> (model! request)
                          (view-publisher-row {:autofocus true})
                          (html/response)))}
     :post {:handler (fn [request]
                       (update-publisher! request))}
     :delete {:handler (fn [request]
                         (delete-publisher! request))}}]
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
