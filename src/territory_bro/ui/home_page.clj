;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.home-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn model! [request]
  (let [congregations (:body (api/list-congregations request))]
    {:congregations (->> congregations
                         (sort-by (comp str/lower-case :name)))}))

(defn view [{:keys [congregations]}]
  (h/html
   [:h1 "Territory Bro"]
   [:p (-> (i18n/t "HomePage.introduction")
           (str/replace "<0>" "<a href=\"https://territorybro.com\">")
           (str/replace "</0>" "</a>")
           (h/raw))]
   ;; TODO: this is an MVP - migrate the rest of the page
   (when-not (empty? congregations)
     (h/html
      [:h2 (i18n/t "HomePage.yourCongregations")]
      [:ul#congregation-list
       (for [congregation congregations]
         [:li {:style {:font-size "150%"}}
          [:a {:href (str "/congregation/" (:id congregation))}
           (:name congregation)]])]))))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}}])
