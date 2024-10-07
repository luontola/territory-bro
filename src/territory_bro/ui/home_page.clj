;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.home-page
  (:require [clojure.java.io :as io]
            [hiccup2.core :as h]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.markdown :as markdown]))

(defn model! [_request]
  (let [congregations (->> (dmz/list-congregations)
                           (mapv #(select-keys % [:congregation/id :congregation/name])))]
    {:congregations congregations
     :logged-in? (auth/logged-in?)}))

(defn login-button []
  (h/html [:a.pure-button {:href "/login"}
           (i18n/t "Navigation.login")]))

(defn register-button []
  (h/html [:a.pure-button {:href "/register"}
           (i18n/t "RegistrationPage.title")]))

(defn join-button []
  (h/html [:a.pure-button {:href "/join"}
           (i18n/t "JoinPage.title")]))

(defn my-congregations-sidebar [{:keys [congregations logged-in?]}]
  (let [styles (:HomePage (css/modules))]
    (h/html
     (if (empty? congregations)
       [:div {:class (html/classes (:sidebar styles) (:bigActions styles))}
        (when-not logged-in?
          [:p (login-button)])
        [:p (register-button)]
        [:p (join-button)]]

       [:div {:class (:sidebar styles)}
        [:h2 (i18n/t "HomePage.yourCongregations")]
        [:ul#congregation-list {:class (:congregationList styles)}
         (for [congregation congregations]
           [:li [:a {:href (str "/congregation/" (:congregation/id congregation))}
                 (:congregation/name congregation)]])]
        [:p {:class (:smallActions styles)}
         (register-button)
         " "
         (join-button)]]))))

(def home-html
  (resources/auto-refresher (io/resource "public/home.md")
                            markdown/render-resource))

(defn view [model]
  (h/html
   [:h1 "Territory Bro"]
   (my-congregations-sidebar model)
   (home-html)))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request {:main-content-variant :narrow})
                         (html/response)))}}])
