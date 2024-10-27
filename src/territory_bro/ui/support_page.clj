;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.support-page
  (:require [clojure.string :as str]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn model! [_request]
  {:support-email (when (auth/logged-in?)
                    (:support-email config/env))})

(defn view [{:keys [support-email]}]
  (h/html
   [:h1 (i18n/t "SupportPage.title")]
   [:p (-> (i18n/t "SupportPage.introduction")
           (str/replace "<0>" "<a href=\"https://github.com/luontola/territory-bro\" target=\"_blank\">")
           (str/replace "</0>" "</a>")
           (str/replace "<1>" "<a href=\"https://www.luontola.fi/about\" target=\"_blank\">")
           (str/replace "</1>" "</a>")
           (h/raw))]
   [:p (-> (i18n/t "SupportPage.mailingListAd")
           (str/replace "<0>" "<a href=\"https://groups.google.com/g/territory-bro-announcements\" target=\"_blank\">")
           (str/replace "</0>" "</a>")
           (h/raw))]
   [:p (-> (i18n/t "SupportPage.userGuideAd")
           (str/replace "<0>" "<a href=\"/documentation\">")
           (str/replace "</0>" "</a>")
           (h/raw))]
   (when (some? support-email)
     [:p (-> (i18n/t "SupportPage.emailAd")
             (str/replace "<0>{{email}}</0>" (str (h/html [:a {:href (str "mailto:" support-email)}
                                                           support-email])))
             (h/raw))])
   [:p (-> (i18n/t "SupportPage.translationAd")
           (str/replace "<0>" "<a href=\"https://github.com/luontola/territory-bro/tree/main/web/src/locales#readme\" target=\"_blank\">")
           (str/replace "</0>" "</a>")
           (h/raw))]
   [:p (-> (i18n/t "SupportPage.issueTrackerAd")
           (str/replace "<0>" "<a href=\"https://github.com/luontola/territory-bro/issues\" target=\"_blank\">")
           (str/replace "</0>" "</a>")
           (h/raw))]
   [:p [:a {:href "/privacy-policy"}
        "Privacy policy"]]))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/support"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}}])
