;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.home-page
  (:require [clojure.java.io :as io]
            [hiccup2.core :as h]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.markdown :as markdown]))

(defn model! [_request]
  (let [congregations (->> (dmz/list-congregations)
                           (mapv #(select-keys % [:congregation/id :congregation/name])))]
    {:congregations congregations}))

(defn my-congregations-sidebar [{:keys [congregations]}]
  (when-not (empty? congregations)
    (let [styles (:HomePage (css/modules))]
      (h/html
       [:div {:class (:sidebar styles)}
        [:h2 (i18n/t "HomePage.yourCongregations")]
        [:ul#congregation-list {:class (:congregationList styles)}
         (for [congregation congregations]
           [:li [:a {:href (str "/congregation/" (:congregation/id congregation))}
                 (:congregation/name congregation)]])]]))))

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
