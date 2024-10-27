;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.documentation-page
  (:require [clojure.java.io :as io]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.markdown :as markdown]))

(def documentation-html
  (resources/auto-refresher (io/resource "public/documentation.md")
                            markdown/render-resource))

(defn view []
  (h/html
   [:h1 (i18n/t "DocumentationPage.title")]
   [:dev {:lang "en"}
    (documentation-html)]))

(def routes
  ["/documentation"
   {:get {:handler (fn [request]
                     (-> (view)
                         (layout/page! request)
                         (html/response)))}}])
