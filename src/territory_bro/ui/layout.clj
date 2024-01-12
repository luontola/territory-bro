;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.i18n :as i18n]))

(defn- minify-html [html]
  (-> html
      (str/trim)
      (str/replace #"(?s)(>)[^<>]*(<)" "$1$2")))

(def ^:private *head-injections (atom {:resource (io/resource "public/index.html")}))

(defn head-injections []
  ;; XXX: make auto-refresh work for non-map values
  (:html (resources/auto-refresh *head-injections (fn [resource]
                                                    (let [[_ html] (re-find #"(?s)</title>(.*)</head>" (slurp resource))]
                                                      {:html (h/raw (minify-html html))})))))


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
       (head-injections)]
      [:body
       [:nav.no-print {:class (:navbar styles)}
        [:HomeNav]
        [:CongregationNav]
        [:div {:class (:lang styles)}
         [:LanguageSelection]]
        [:div {:class (:auth styles)}
         [:AuthenticationPanel]]]

       [:dialog#htmx-error-dialog
        [:h2 (i18n/t "Errors.unknownError")]
        [:p#htmx-error-message {:data-default-message (i18n/t "Errors.reloadAndTryAgain")}]
        [:form {:method "dialog"}
         [:button.pure-button.pure-button-primary {:type "submit"}
          (i18n/t "Errors.closeDialog")]]]

       [:main {:class (:content styles)}
        content]]])))
