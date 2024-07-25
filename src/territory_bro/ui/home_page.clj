;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.home-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn model! [request]
  (let [congregations (:body (api/list-congregations request))]
    {:congregations (->> congregations
                         (sort-by (comp str/lower-case :congregation/name)))
     :logged-in? (auth/logged-in?)
     :demo-available? (some? (:demo-congregation config/env))}))

(defn login-button []
  (h/html [:a.pure-button {:href "/login"}
           (i18n/t "Navigation.login")]))

(defn view-demo-button []
  (h/html [:a.pure-button {:href "/congregation/demo"}
           (i18n/t "HomePage.viewDemo")]))

(defn register-button []
  (h/html [:a.pure-button {:href "/register"}
           (i18n/t "RegistrationPage.title")]))

(defn join-button []
  (h/html [:a.pure-button {:href "/join"}
           (i18n/t "JoinPage.title")]))

(defn view [{:keys [congregations logged-in? demo-available?]}]
  (let [styles (:HomePage (css/modules))]
    (h/html
     [:h1 "Territory Bro"]
     [:p (-> (i18n/t "HomePage.introduction")
             (str/replace "<0>" "<a href=\"https://territorybro.com\">")
             (str/replace "</0>" "</a>")
             (h/raw))]

     (if (empty? congregations)
       [:div {:class (:bigActions styles)}
        (when-not logged-in?
          [:p (login-button)])
        (when demo-available?
          [:p (view-demo-button)])
        [:p (register-button)]
        [:p (join-button)]]

       ; TODO: use h/html instead of :div after fixing https://github.com/weavejester/hiccup/issues/210
       [:div
        [:h2 (i18n/t "HomePage.yourCongregations")]
        [:ul#congregation-list {:class (:congregationList styles)}
         (for [congregation congregations]
           [:li [:a {:href (str "/congregation/" (:congregation/id congregation))}
                 (:congregation/name congregation)]])]
        [:p {:class (:smallActions styles)}
         (when demo-available?
           (view-demo-button))
         " "
         (register-button)
         " "
         (join-button)]]))))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}}])
