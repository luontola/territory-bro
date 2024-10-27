;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout
  (:require [clojure.string :as str]
            [hiccup.page :as hiccup.page]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        language-selection-width (get-in request [:cookies "languageSelectionWidth" :value])]
    {:congregation (when (some? cong-id)
                     (-> (dmz/get-congregation cong-id)
                         (select-keys [:congregation/id :congregation/name])))
     :permissions (when (some? cong-id)
                    {:view-printouts-page (dmz/view-printouts-page? cong-id)
                     :view-settings-page (dmz/view-settings-page? cong-id)})
     :user (when (auth/logged-in?)
             auth/*user*)
     :login-url (when-not (auth/logged-in?)
                  (auth0/login-url request))
     :language-selection-width language-selection-width
     :dev? (:dev config/env)
     :demo-available? (some? (:demo-congregation config/env))
     :demo? (= "demo" cong-id)}))


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

(defn external-link [{:keys [href icon title]}]
  (h/html
   [:a {:href href
        :target "_blank"
        :title (i18n/t "Navigation.opensInNewWindow")}
    [:span {:aria-hidden true} icon]
    " "
    title
    " "
    (html/inline-svg "icons/external-link.svg")]))

(defn congregation-navigation [{:keys [congregation permissions demo?]}]
  (let [cong-id (:congregation/id congregation)
        styles (:Layout (css/modules))]
    (h/html
     [:li (nav-link {:href (str "/congregation/" cong-id)
                     :title (if demo?
                              (str "🔍 " (i18n/t "Navigation.demo"))
                              (:congregation/name congregation))})]
     [:ul {:class (:nav-submenu styles)}
      [:li (nav-link {:href (str "/congregation/" cong-id "/territories")
                      :icon "📍"
                      :title (i18n/t "TerritoryListPage.title")})]
      (when (:view-printouts-page permissions)
        [:li (nav-link {:href (str "/congregation/" cong-id "/printouts")
                        :icon "🖨️"
                        :title (i18n/t "PrintoutPage.title")})])
      (when (:view-settings-page permissions)
        [:li (nav-link {:href (str "/congregation/" cong-id "/settings")
                        :icon "⚙️"
                        :title (i18n/t "SettingsPage.title")})])])))

(defn navigation [{:keys [congregation demo-available? demo?] :as model}]
  (let [styles (:Layout (css/modules))]
    (h/html
     [:ul {:class (:nav-menu styles)}
      [:li (nav-link {:href "/"
                      :icon "🏠"
                      :title (i18n/t "HomePage.title")})]
      (when (some? congregation)
        (congregation-navigation model))
      (when (and demo-available? (not demo?))
        [:li (nav-link {:href "/congregation/demo"
                        :icon "🔍"
                        :title (i18n/t "Navigation.demo")})])
      [:li (nav-link {:href "/documentation"
                      :icon "📖"
                      :title (i18n/t "DocumentationPage.title")})]
      [:li (nav-link {:href "/register"
                      :icon "✍️"
                      :title (i18n/t "Navigation.registration")})]
      [:li (external-link {:href "https://groups.google.com/g/territory-bro-announcements"
                           :icon "📢"
                           :title (i18n/t "Navigation.news")})]
      [:li (nav-link {:href "/support"
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
    (str (h/html
          (assert (= :html hiccup.util/*html-mode*))
          (hiccup.page/doctype :html5)
          [:html {:lang (name i18n/*lang*)}
           [:head
            [:meta {:charset "utf-8"}]
            [:title
             (when (and (some? title)
                        (not= "Territory Bro" title))
               (str title " - "))
             "Territory Bro"]
            [:meta {:name "viewport"
                    :content "width=device-width, initial-scale=1"}]
            [:link {:rel "stylesheet"
                    :href "https://fonts.googleapis.com/css2?family=Noto+Color+Emoji&display=swap"
                    :crossorigin "anonymous"
                    :referrerpolicy "no-referrer"}]
            [:link {:rel "icon"
                    :href (get html/public-resources "/assets/logo-small.*.svg")}]
            [:link {:rel "canonical"
                    :href (str (:public-url config/env) html/*page-path*)}]
            (head-injections)
            (:head model)]
           [:body {:hx-headers (html/anti-forgery-headers-json)}
            [:nav.no-print {:class (:navbar styles)}
             [:div {:class (:logo styles)}
              [:a {:href "/"}
               [:picture
                [:source {:type "image/svg+xml"
                          :srcset (get html/public-resources "/assets/logo-big.*.svg")}]
                [:img {:src (get html/public-resources "/assets/logo-big.*.png")
                       :alt "Territory Bro logo"}]]]]
             (navigation model)
             [:div {:class (:nav-end styles)}
              [:div {:class (:lang styles)}
               (language-selection model)]
              [:div {:class (:auth styles)}
               (authentication-panel model)]]]

            [:dialog#htmx-error-dialog
             [:h2 (i18n/t "Errors.unknownError")]
             [:p#htmx-error-message {:data-default-message (i18n/t "Errors.reloadAndTryAgain")}]
             [:form {:method "dialog"}
              [:button.pure-button.pure-button-primary {:type "submit"}
               (i18n/t "Errors.closeDialog")]]]

            [:main {:class (html/classes (:content styles)
                                         (get styles (or (:main-content-variant model)
                                                         :narrow)))}
             (when (:demo? model)
               (demo-disclaimer))
             view]]]))))

(defn page!
  ([view request]
   (page! view request nil))
  ([view request opts]
   (page view (merge (model! request) opts))))
