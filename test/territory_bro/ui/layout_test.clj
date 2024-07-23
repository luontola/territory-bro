;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [hiccup2.core :as h]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout])
  (:import (java.util UUID)))

(def anonymous-model
  {:congregation nil
   :user nil
   ;; the page path and query string should both be included in the return-to-url
   :login-url "/login?return-to-url=%2Fsome%2Fpage%3Ffoo%3Dbar%26gazonk"
   :language-selection-width "42px"
   :dev? false
   :demo? false})
(def developer-model
  {:congregation nil
   :user nil
   :login-url "/login?return-to-url=%2F"
   :language-selection-width nil
   :dev? true
   :demo? false})
(def logged-in-model
  {:congregation nil
   :user {:user/id (UUID. 0 2)
          :name "John Doe"}
   :login-url nil
   :language-selection-width nil
   :dev? false
   :demo? false})
(def congregation-model
  {:congregation {:congregation/id (UUID. 0 1)
                  :congregation/name "the congregation"
                  :congregation/permissions {:configure-congregation true
                                             :edit-do-not-calls true
                                             :gis-access true
                                             :share-territory-link true
                                             :view-congregation true}}
   :user {:user/id (UUID. 0 2)
          :name "John Doe"}
   :login-url nil
   :language-selection-width nil
   :dev? false
   :demo? false})
(def demo-congregation-model
  {:congregation {:congregation/id "demo"
                  :congregation/name "Demo Congregation"
                  :congregation/permissions {:share-territory-link true
                                             :view-congregation true}}
   :user nil
   :login-url "/login?return-to-url=%2Fcongregation%2Fdemo"
   :language-selection-width nil
   :dev? false
   :demo? true})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "the congregation")]

      (testing "top level, anonymous"
        (let [request {:uri "/some/page"
                       :query-string "foo=bar&gazonk"
                       :cookies {"languageSelectionWidth" {:value "42px"}}}]
          (is (= anonymous-model
                 (layout/model! request)))))

      (testing "top level, developer"
        (binding [config/env (replace-in config/env [:dev] false true)]
          (let [request {:uri "/"
                         :query-string nil}]
            (is (= developer-model
                   (layout/model! request))))))

      (testing "top level, logged in"
        (let [request {:uri "/"
                       :query-string nil
                       :session (auth/user-session {:name "John Doe"} user-id)}]
          (is (= (-> logged-in-model
                     (replace-in [:user :user/id] (UUID. 0 2) user-id))
                 (layout/model! request)))))

      (testing "congregation level"
        (let [request {:uri "/"
                       :query-string nil
                       :params {:congregation (str cong-id)}
                       :session (auth/user-session {:name "John Doe"} user-id)}]
          (is (= (-> congregation-model
                     (replace-in [:congregation :congregation/id] (UUID. 0 1) cong-id)
                     (replace-in [:user :user/id] (UUID. 0 2) user-id))
                 (layout/model! request)))))

      (testing "demo congregation"
        (binding [config/env (replace-in config/env [:demo-congregation] nil cong-id)]
          (let [request {:uri "/congregation/demo"
                         :query-string nil
                         :params {:congregation "demo"}}]
            (is (= demo-congregation-model
                   (layout/model! request)))))))))

(deftest page-test
  (testing "minimal data"
    (is (= (html/normalize-whitespace
            "Territory Bro

             🏠 Home
             User guide {external-link.svg}
             News {external-link.svg}
             🛟 Support

             {language.svg} Change language [English]
             Login

             Sorry, something went wrong 🥺
             Close")
           (-> (layout/page nil nil)
               (html/visible-text)))))

  (testing "top-level navigation"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             User guide {external-link.svg}
             News {external-link.svg}
             🛟 Support

             {language.svg} Change language [English]
             {user.svg} John Doe
             Logout

             Sorry, something went wrong 🥺
             Close

             the title
             the content")
           (-> (h/html [:h1 "the title"]
                       [:p "the content"])
               (layout/page logged-in-model)
               (html/visible-text)))))

  (testing "congregation-level navigation"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             the congregation
             📍 Territories
             🖨️ Printouts
             ⚙️ Settings
             🛟 Support

             {language.svg} Change language [English]
             {user.svg} John Doe
             Logout

             Sorry, something went wrong 🥺
             Close

             the title
             the content")
           (-> (h/html [:h1 "the title"]
                       [:p "the content"])
               (layout/page congregation-model)
               (html/visible-text))))

    (testing "with minimum permissions"
      (is (= (html/normalize-whitespace
              "the title - Territory Bro

               🏠 Home
               the congregation
               📍 Territories
               🛟 Support

               {language.svg} Change language [English]
               {user.svg} John Doe
               Logout

               Sorry, something went wrong 🥺
               Close

               the title
               the content")
             (-> (h/html [:h1 "the title"]
                         [:p "the content"])
                 (layout/page (assoc-in congregation-model [:congregation :congregation/permissions] {}))
                 (html/visible-text))))))

  (testing "demo congregation"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             Demo Congregation
             📍 Territories
             🖨️ Printouts
             🛟 Support

             {language.svg} Change language [English]
             Login

             Sorry, something went wrong 🥺
             Close

             {info.svg} Welcome to the demo
             This demo is limited to only viewing a congregation. Some features are restricted.

             the title
             the content")
           (-> (h/html [:h1 "the title"]
                       [:p "the content"])
               (layout/page demo-congregation-model)
               (html/visible-text)))))

  (testing "current language is defined in the <html> element"
    (is (str/includes? (layout/page nil nil) "<html lang=\"en\">"))
    (binding [i18n/*lang* :fi]
      (is (str/includes? (layout/page nil nil) "<html lang=\"fi\">")))))


;;;; Components and helpers

(deftest page-title-test
  (testing "page without <h1>"
    (let [page (-> (h/html [:p "no <h1>"])
                   (layout/page nil))]
      (is (str/includes? page "<title>Territory Bro</title>")
          "show only application name")))

  (testing "page with <h1>"
    (let [page (-> (h/html [:h1 "Page Title"])
                   (layout/page nil))]
      (is (str/includes? page "<title>Page Title - Territory Bro</title>")
          "show page title before application name")))

  (testing "page with <h1> equal to application name, as on the home page"
    (let [page (-> (h/html [:h1 "Territory Bro"])
                   (layout/page nil))]
      (is (str/includes? page
                         "<title>Territory Bro</title>")
          "show only application name"))))

(deftest active-link?-test
  (testing "on home page"
    (let [current-page "/"]
      (is (true? (layout/active-link? "/" current-page)))
      (is (false? (layout/active-link? "/support" current-page)))
      (is (false? (layout/active-link? "/congregation/123" current-page)))
      (is (false? (layout/active-link? "/congregation/123/territories" current-page)))))

  (testing "on another global page"
    (let [current-page "/support"]
      (is (false? (layout/active-link? "/" current-page)))
      (is (true? (layout/active-link? "/support" current-page)))
      (is (false? (layout/active-link? "/congregation/123" current-page)))
      (is (false? (layout/active-link? "/congregation/123/territories" current-page)))))

  (testing "on congregation home page"
    (let [current-page "/congregation/123"]
      (is (false? (layout/active-link? "/" current-page)))
      (is (false? (layout/active-link? "/support" current-page)))
      (is (true? (layout/active-link? "/congregation/123" current-page)))
      (is (false? (layout/active-link? "/congregation/123/territories" current-page)))))

  (testing "on another congregation page"
    (let [current-page "/congregation/123/territories"]
      (is (false? (layout/active-link? "/" current-page)))
      (is (false? (layout/active-link? "/support" current-page)))
      (is (true? (layout/active-link? "/congregation/123" current-page)))
      (is (true? (layout/active-link? "/congregation/123/territories" current-page))))))

(deftest authentication-panel-test
  (testing "anonymous"
    (let [html (layout/authentication-panel anonymous-model)]
      (is (= "Login"
             (html/visible-text html)))
      (is (str/includes? (str html) "href=\"/login?return-to-url=%2Fsome%2Fpage%3Ffoo%3Dbar%26gazonk\"")
          "the login link will redirect back to the current page after login")))

  (testing "developer"
    (is (= (html/normalize-whitespace
            "Login
             Dev Login")
           (html/visible-text
            (layout/authentication-panel developer-model)))))

  (testing "logged in"
    (is (= (html/normalize-whitespace
            "{user.svg} John Doe
             Logout")
           (html/visible-text
            (layout/authentication-panel logged-in-model))))))

(deftest language-selection-test
  (testing "the current language is shown using only its native name"
    (is (= "{language.svg} Change language [English]"
           (html/visible-text
            (layout/language-selection anonymous-model))))
    (binding [i18n/*lang* :fi]
      (is (= "{language.svg} Vaihda kieltä [suomi]"
             (html/visible-text
              (layout/language-selection anonymous-model))))))

  (testing "other languages are shown using their native and English name"
    (binding [i18n/*lang* :xx]
      (is (str/includes? (layout/language-selection anonymous-model)
                         "<option value=\"fi\">suomi - Finnish</option>"))
      (is (str/includes? (layout/language-selection anonymous-model)
                         "<option value=\"en\">English</option>")
          "except English, which is shown only once in English")))

  (testing "default element width to avoid layout flicker"
    (is (str/includes? (layout/language-selection anonymous-model)
                       "style=\"width:42px;\""))
    (is (not (str/includes? (layout/language-selection (dissoc anonymous-model :language-selection-width))
                            "style="))
        "no style if width is not known")))
