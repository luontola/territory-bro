;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout
  (:require [hiccup2.core :as h]))

(defn page [{:keys [title]} content]
  (h/html
   (hiccup.page/doctype :html5)
   [:html {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:title
      (when (some? title)
        (str title " - "))
      "Territory Bro"]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]]
    [:body
     content]]))
