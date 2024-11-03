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
