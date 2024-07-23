;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]
            [territory-bro.ui.visible :as visible]))

(defn model! [request]
  (auth/with-user-from-session request
    (let [cong-id (get-in request [:params :congregation])
          demo? (= "demo" cong-id)
          congregation (when (some? cong-id)
                         (-> (if demo?
                               (:body (api/get-demo-congregation request))
                               (:body (api/get-congregation request {})))
                             (select-keys [:congregation/id :congregation/name :congregation/permissions])))
          language-selection-width (get-in request [:cookies "languageSelectionWidth" :value])]
      {:congregation congregation
       :user (when (auth/logged-in?)
               auth/*user*)
       :login-url (when-not (auth/logged-in?)
                    (auth0/login-url request))
       :language-selection-width language-selection-width
       :dev? (:dev config/env)
       :demo? demo?})))


(defn- minify-html [html]
  (-> html
      (str/trim)
      (str/replace #"(?s)(>)[^<>]*(<)" "$1$2")))

(def head-injections
  (resources/auto-refresher "public/index.html"
                            (fn [resource]
                              (let [[_ html] (re-find #"(?s)</title>(.*)</head>" (slurp resource))]
                                (h/raw (minify-html html))))))

(defn active-link? [href current-page]
  (if (= "/" current-page)
    (= "/" href)
    (and (str/starts-with? current-page href)
         (not= "/" href))))

(defn nav-link [{:keys [href icon title]}]
  (let [styles (:Layout (css/modules))]
    (h/html
     [:a {:href href
          :class (when (active-link? href html/*page-path*)
                   (:active styles))}
      [:span {:aria-hidden true} icon]
      " "
      title])))

(defn external-link [{:keys [href title]}]
  (h/html
   [:a {:href href
        :target "_blank"
        :title (i18n/t "Navigation.opensInNewWindow")}
    title
    " "
    (html/inline-svg "icons/external-link.svg")]))

(defn home-navigation []
  (let [styles (:Layout (css/modules))]
    (h/html
     [:ul {:class (:nav styles)}
      [:li (nav-link {:href "/"
                      :icon "🏠"
                      :title (i18n/t "HomePage.title")})]
      [:li (external-link {:href "https://territorybro.com/guide/"
                           :title (i18n/t "Navigation.userGuide")})]
      [:li (external-link {:href "https://groups.google.com/g/territory-bro-announcements"
                           :title (i18n/t "Navigation.news")})]
      [:li (nav-link {:href "/support"
                      :icon "🛟"
                      :title (i18n/t "SupportPage.title")})]])))

(defn congregation-navigation [{:keys [congregation]}]
  (let [cong-id (:congregation/id congregation)
        styles (:Layout (css/modules))]
    (h/html
     [:ul {:class (:nav styles)}
      [:li (nav-link {:href "/"
                      :icon "🏠"
                      :title (i18n/t "HomePage.title")})]
      [:li (nav-link {:href (str "/congregation/" cong-id)
                      :title (:congregation/name congregation)})]
      [:li (nav-link {:href (str "/congregation/" cong-id "/territories")
                      :icon "📍"
                      :title (i18n/t "TerritoryListPage.title")})]
      (when (visible/printouts-page? (:congregation/permissions congregation))
        [:li (nav-link {:href (str "/congregation/" cong-id "/printouts")
                        :icon "🖨️"
                        :title (i18n/t "PrintoutPage.title")})])
      (when (visible/settings-page? (:congregation/permissions congregation))
        [:li (nav-link {:href (str "/congregation/" cong-id "/settings")
                        :icon "⚙️"
                        :title (i18n/t "SettingsPage.title")})])
      [:li (nav-link {:href (str "/congregation/" cong-id "/support")
                      :icon "🛟"
                      :title (i18n/t "SupportPage.title")})]])))

(defn- format-language-name [{:keys [code englishName nativeName]} current-language]
  (cond
    (= current-language code)
    nativeName

    (= englishName nativeName)
    nativeName

    :else
    (str nativeName " - " englishName)))

(defn language-selection [{:keys [language-selection-width]}]
  (let [styles (:Layout (css/modules))
        current-language (name i18n/*lang*)]
    (h/html
     [:form.pure-form {:method "get"}
      [:label
       (html/inline-svg "icons/language.svg"
                        {:title (i18n/t "Navigation.changeLanguage")
                         :class (:languageSelectionIcon styles)})
       " "
       [:select#language-selection {:name "lang"
                                    :aria-label (i18n/t "Navigation.changeLanguage")
                                    :title (i18n/t "Navigation.changeLanguage")
                                    :class (:languageSelection styles)
                                    :style (when (some? language-selection-width)
                                             {:width language-selection-width})
                                    :onchange "this.form.submit()"}
        (for [language (i18n/languages)]
          [:option {:value (:code language)
                    :selected (= current-language (:code language))}
           (format-language-name language current-language)])]]])))

(defn authentication-panel [{:keys [user login-url dev?]}]
  (if (some? user)
    (h/html (html/inline-svg "icons/user.svg"
                             {:style {:font-size "1.25em"
                                      :vertical-align "middle"}})
            " " (:name user) " "
            [:a#logout-button.pure-button {:href "/logout"}
             (i18n/t "Navigation.logout")])
    (h/html [:a#login-button.pure-button {:href login-url}
             (i18n/t "Navigation.login")]
            (when dev?
              (h/html " " [:a#dev-login-button.pure-button {:href "/dev-login?sub=developer&name=Developer&email=developer@example.com"}
                           "Dev Login"])))))

(defn demo-disclaimer []
  (h/html
   [:div.no-print
    (info-box/view {:title (i18n/t "DemoDisclaimer.welcome")}
                   (h/html
                    [:p (i18n/t "DemoDisclaimer.introduction")]))]))

(defn- parse-title [view]
  (second (re-find #"<h1>(.*?)</h1>" (str view))))

(defn page [view model]
  (let [styles (:Layout (css/modules))
        title (parse-title view)]
    (assert (= :html hiccup.util/*html-mode*))
    (str (h/html
          (hiccup.page/doctype :html5)
          [:html {:lang (name i18n/*lang*)}
           [:head
            [:meta {:charset "utf-8"}]
            [:title
             (when (and (some? title)
                        (not= "Territory Bro" title))
               (str title " - "))
             "Territory Bro"]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
            (head-injections)]
           [:body
            [:nav.no-print {:class (:navbar styles)}
             (if (some? (:congregation model))
               (congregation-navigation model)
               (home-navigation))
             [:div {:class (:lang styles)}
              (language-selection model)]
             [:div {:class (:auth styles)}
              (authentication-panel model)]]

            [:dialog#htmx-error-dialog
             [:h2 (i18n/t "Errors.unknownError")]
             [:p#htmx-error-message {:data-default-message (i18n/t "Errors.reloadAndTryAgain")}]
             [:form {:method "dialog"}
              [:button.pure-button.pure-button-primary {:type "submit"}
               (i18n/t "Errors.closeDialog")]]]

            [:main {:class (:content styles)}
             (when (:demo? model)
               (demo-disclaimer))
             view]]]))))

(defn page! [view request]
  (page view (model! request)))
