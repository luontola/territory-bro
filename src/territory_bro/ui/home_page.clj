(ns territory-bro.ui.home-page
  (:require [clojure.java.io :as io]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
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
        [:h2 {} (i18n/t "HomePage.yourCongregations")]
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
   [:div {:lang "en"}
    (home-html)]))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}}])
