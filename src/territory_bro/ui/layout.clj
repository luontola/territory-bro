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
