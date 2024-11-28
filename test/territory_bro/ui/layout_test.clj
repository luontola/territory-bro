(ns territory-bro.ui.layout-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.demo :as demo]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout])
  (:import (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def anonymous-model
  {:congregation nil
   :permissions nil
   :user nil
   ;; the page path and query string should both be included in the return-to-url
   :login-url "/login?return-to-url=%2Fsome%2Fpage%3Ffoo%3Dbar%26gazonk"
   :language-selection-width "42px"
   :dev? false
   :demo-available? true
   :demo? false})
(def no-demo-model
  {:congregation nil
   :permissions nil
   :user nil
   :login-url "/login?return-to-url=%2F"
   :language-selection-width nil
   :dev? false
   :demo-available? false
   :demo? false})
(def developer-model
  {:congregation nil
   :permissions nil
   :user nil
   :login-url "/login?return-to-url=%2F"
   :language-selection-width nil
   :dev? true
   :demo-available? true
   :demo? false})
(def logged-in-model
  {:congregation nil
   :permissions nil
   :user {:user/id user-id
          :name "John Doe"}
   :login-url nil
   :language-selection-width nil
   :dev? false
   :demo-available? true
   :demo? false})
(def congregation-model
  {:congregation {:congregation/id cong-id
                  :congregation/name "the congregation"}
   :permissions {:view-printouts-page true
                 :view-settings-page true}
   :user {:user/id user-id
          :name "John Doe"}
   :login-url nil
   :language-selection-width nil
   :dev? false
   :demo-available? true
   :demo? false})
(def demo-congregation-model
  {:congregation {:congregation/id "demo"
                  :congregation/name "Demo Congregation"}
   :permissions {:view-printouts-page true
                 :view-settings-page false}
   :user nil
   :login-url "/login?return-to-url=%2Fcongregation%2Fdemo"
   :language-selection-width nil
   :dev? false
   :demo-available? true
   :demo? true})

(deftest model!-test
  (binding [config/env {:dev false
                        :demo-congregation cong-id}]
    (testutil/with-events (flatten [{:event/type :congregation.event/congregation-created
                                     :congregation/id cong-id
                                     :congregation/name "the congregation"
                                     :congregation/schema-name "cong_schema"}
                                    (congregation/admin-permissions-granted cong-id user-id)
                                    demo/congregation-created])

      (testing "top level, anonymous"
        (testutil/with-anonymous-user
          (let [request {:uri "/some/page"
                         :query-string "foo=bar&gazonk"
                         :cookies {"languageSelectionWidth" {:value "42px"}}}]
            (is (= anonymous-model (layout/model! request))))))

      (testing "top level, anonymous, no demo"
        (binding [config/env (replace-in config/env [:demo-congregation] cong-id nil)]
          (testutil/with-anonymous-user
            (let [request {:uri "/"
                           :query-string nil}]
              (is (= no-demo-model (layout/model! request)))))))

      (testing "top level, anonymous, developer mode"
        (testutil/with-anonymous-user
          (binding [config/env (replace-in config/env [:dev] false true)]
            (let [request {:uri "/"
                           :query-string nil}]
              (is (= developer-model (layout/model! request)))))))

      (testing "top level, logged in"
        (auth/with-user {:user/id user-id
                         :name "John Doe"}
          (let [request {:uri "/"
                         :query-string nil}]
            (is (= logged-in-model (layout/model! request))))))

      (testing "congregation level"
        (auth/with-user {:user/id user-id
                         :name "John Doe"}
          (let [request {:uri "/"
                         :query-string nil
                         :path-params {:congregation cong-id}}]
            (is (= congregation-model (layout/model! request))))))

      (testing "demo congregation"
        (testutil/with-anonymous-user
          (let [request {:uri "/congregation/demo"
                         :query-string nil
                         :path-params {:congregation "demo"}}]
            (is (= demo-congregation-model (layout/model! request)))))))))

(deftest page-test
  (testing "minimal data"
    (is (= (html/normalize-whitespace
            "Territory Bro

             🏠 Home
             📖 Documentation
             ✍️ Registration
             📢 News {external-link.svg}
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
             🔍 Demo
             📖 Documentation
             ✍️ Registration
             📢 News {external-link.svg}
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
            "the title - the congregation - Territory Bro

             🏠 Home
             the congregation
               📍 Territories
               🖨️ Printouts
               ⚙️ Settings
             🔍 Demo
             📖 Documentation
             ✍️ Registration
             📢 News {external-link.svg}
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
              "the title - the congregation - Territory Bro

               🏠 Home
               the congregation
                 📍 Territories
               🔍 Demo
               📖 Documentation
               ✍️ Registration
               📢 News {external-link.svg}
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
                 (layout/page (dissoc congregation-model :permissions))
                 (html/visible-text))))))

  (testing "demo congregation"
    (is (= (html/normalize-whitespace
            "the title - Demo Congregation - Territory Bro

             🏠 Home
             🔍 Demo
               📍 Territories
               🖨️ Printouts
             📖 Documentation
             ✍️ Registration
             📢 News {external-link.svg}
             🛟 Support

             {language.svg} Change language [English]
             Login

             Sorry, something went wrong 🥺
             Close

             {info.svg} Welcome to the demo
             Only you will see the changes you make to this demo congregation.

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

(deftest head-content-test
  (testing "default head"
    (let [page (layout/page nil nil)]
      (is (str/includes? page (str layout/default-head)))))

  (testing "custom head overrides the default"
    (let [custom-head (h/html
                       [:meta {:name "description"
                               :content "My custom description."}])
          page (layout/page nil {:head custom-head})]
      (is (not (str/includes? page (str layout/default-head))))
      (is (str/includes? page (str custom-head))))))

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
