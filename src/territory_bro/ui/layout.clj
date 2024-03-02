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
            [territory-bro.ui.i18n :as i18n]))

(defn model! [request {:keys [title]}]
  (auth/with-user-from-session request
    (let [congregation (when (some? (get-in request [:params :congregation]))
                         (-> (:body (api/get-congregation request))
                             (select-keys [:id :name])))]
      {:title title
       :congregation congregation
       :user (when (auth/logged-in?)
               auth/*user*)
       :login-url (when-not (auth/logged-in?)
                    (auth0/login-url request))
       :dev? (:dev config/env)})))


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
    [:i.fa-solid.fa-external-link-alt]]))

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
  (let [cong-id (:id congregation)
        styles (:Layout (css/modules))]
    (h/html
     [:ul {:class (:nav styles)}
      [:li (nav-link {:href "/"
                      :icon "🏠"
                      :title (i18n/t "HomePage.title")})]
      [:li (nav-link {:href (str "/congregation/" cong-id)
                      :title (:name congregation)})]
      [:li (nav-link {:href (str "/congregation/" cong-id "/territories")
                      :icon "📍"
                      :title (i18n/t "TerritoryListPage.title")})]
      (when true ; TODO: congregation.permissions.viewCongregation
        [:li (nav-link {:href (str "/congregation/" cong-id "/printouts")
                        :icon "🖨️"
                        :title (i18n/t "PrintoutPage.title")})])
      (when true ; TODO: (congregation.permissions.configureCongregation || congregation.permissions.gisAccess)
        [:li (nav-link {:href (str "/congregation/" cong-id "/settings")
                        :icon "⚙️"
                        :title (i18n/t "SettingsPage.title")})])
      [:li (nav-link {:href (str "/congregation/" cong-id "/support")
                      :icon "🛟"
                      :title (i18n/t "SupportPage.title")})]])))

(defn authentication-panel [{:keys [user login-url dev?]}]
  (if (some? user)
    (h/html [:i.fa-solid.fa-user-large {:style {:font-size "1.25em"
                                                :vertical-align "middle"}}]
            " " (:name user) " "
            [:a#logout-button.pure-button {:href "/logout"}
             (i18n/t "Navigation.logout")])
    (h/html [:a#login-button.pure-button {:href login-url}
             (i18n/t "Navigation.login")]
            (when dev?
              (h/html " " [:a#dev-login-button.pure-button {:href "/dev-login?sub=developer&name=Developer&email=developer@example.com"}
                           "Dev Login"])))))

(defn page [{:keys [title congregation] :as model} view]
  (let [styles (:Layout (css/modules))]
    (str (h/html
          (hiccup.page/doctype :html5)
          [:html {:lang "en"}
           [:head
            [:meta {:charset "utf-8"}]
            [:title
             (when (some? title)
               (str title " - "))
             "Territory Bro"]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
            ;; https://fontawesome.com/v5/docs/web/use-with/wordpress/install-manually#set-up-svg-with-cdn
            ;; https://cdnjs.com/libraries/font-awesome
            [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/js/solid.min.js"
                      :integrity "sha512-+fI924YJzeYFv7M0R29zJvRThPinSUOAmo5rpR9v6G4eWIbva/prHdZGSPN440vuf781/sOd/Fr+5ey0pqdW9w=="
                      :defer true
                      :crossorigin "anonymous"
                      :referrerpolicy "no-referrer"}]
            [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/js/regular.min.js"
                      :integrity "sha512-T4H/jsKWzCRypzaFpVpYyWyBUhjKfp5e/hSD234qFO/h45wKAXba+0wG/iFRq1RhybT7dXxjPYYBYCLAwPfE0Q=="
                      :defer true
                      :crossorigin "anonymous"
                      :referrerpolicy "no-referrer"}]
            [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/js/fontawesome.min.js"
                      :integrity "sha512-C8qHv0HOaf4yoA7ISuuCTrsPX8qjolYTZyoFRKNA9dFKnxgzIHnYTOJhXQIt6zwpIFzCrRzUBuVgtC4e5K1nhA=="
                      :defer true
                      :crossorigin "anonymous"
                      :referrerpolicy "no-referrer"}]
            (head-injections)]
           [:body
            [:nav.no-print {:class (:navbar styles)}
             (if (some? congregation)
               (congregation-navigation model)
               (home-navigation))
             [:div {:class (:lang styles)}
              [:LanguageSelection]] ; TODO
             [:div {:class (:auth styles)}
              (authentication-panel model)]]

            [:dialog#htmx-error-dialog
             [:h2 (i18n/t "Errors.unknownError")]
             [:p#htmx-error-message {:data-default-message (i18n/t "Errors.reloadAndTryAgain")}]
             [:form {:method "dialog"}
              [:button.pure-button.pure-button-primary {:type "submit"}
               (i18n/t "Errors.closeDialog")]]]

            [:main {:class (:content styles)}
             view]]]))))

(defn page! [request opts view]
  ;; TODO: read title from the content, so we can remove the opts parameter
  (page (model! request opts)
    view))
