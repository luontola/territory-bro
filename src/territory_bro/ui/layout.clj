;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout
  (:require [hiccup2.core :as h]
            [territory-bro.ui.css :as css]))

(defn page [{:keys [title]} content]
  (let [styles (:Layout (css/modules))]
    (h/html
     (hiccup.page/doctype :html5)
     [:html {:lang "en"}
      [:head
       [:meta {:charset "utf-8"}]
       [:title
        (when (some? title)
          (str title " - "))
        "Territory Bro"]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:link {:rel "stylesheet" :href (css/stylesheet-path)}]]
      [:body
       [:nav {:class (css/classes (:navbar styles) "no-print")}
        [:HomeNav]
        [:CongregationNav]
        [:div {:class (:lang styles)}
         [:LanguageSelection]]
        [:div {:class (:auth styles)}
         [:AuthenticationPanel]]]

       [:main {:class (:content styles)}
        content]]])))
