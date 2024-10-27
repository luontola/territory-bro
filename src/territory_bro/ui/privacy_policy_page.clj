;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.privacy-policy-page
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.markdown :as markdown]))

(def content-html
  (resources/auto-refresher (io/resource "public/privacy-policy.md")
                            markdown/render-resource))

(defmacro last-updated-date []
  (:out (shell/sh "git" "log" "-1" "--pretty=format:%cs" "resources/public/privacy-policy.md")))

(defn view []
  (let [email (str "privacy-policy" (html/inline-svg "icons/at.svg") "territorybro.com")]
    (h/html
     [:h1 "Privacy policy"]
     (-> (content-html)
         (str/replace "<privacy-policy-email>" email)
         (str/replace "<last-updated>" (last-updated-date))
         (h/raw)))))

(def routes
  ["/privacy-policy"
   {:get {:handler (fn [request]
                     (-> (view)
                         (layout/page! request)
                         (html/response)))}}])
