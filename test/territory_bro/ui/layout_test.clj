;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout-test
  (:require [clojure.test :refer :all]
            [hiccup2.core :as h]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.layout :as layout])
  (:import (java.util UUID)))

(def anonymous-model
  {:title "the title"
   :congregation nil
   :user nil
   :dev? false})
(def developer-model
  {:title "the title"
   :congregation nil
   :user nil
   :dev? true})
(def logged-in-model
  {:title "the title"
   :congregation nil
   :user {:user/id (UUID. 0 2)
          :name "John Doe"}
   :dev? false})
(def congregation-model
  {:title "the title"
   :congregation {:id (UUID. 0 1)
                  :name "the congregation"}
   :user {:user/id (UUID. 0 2)
          :name "John Doe"}
   :dev? false})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "the congregation")]

      (testing "top level, anonymous"
        (let [request {}]
          (is (= anonymous-model
                 (layout/model! request {:title "the title"})))))

      (testing "top level, developer"
        (binding [config/env (replace-in config/env [:dev] false true)]
          (let [request {}]
            (is (= developer-model
                   (layout/model! request {:title "the title"}))))))

      (testing "top level, logged in"
        (let [request {:session (auth/user-session {:name "John Doe"} user-id)}]
          (is (= (-> logged-in-model
                     (replace-in [:user :user/id] (UUID. 0 2) user-id))
                 (layout/model! request {:title "the title"})))))

      (testing "congregation level"
        (let [request {:params {:congregation (str cong-id)}
                       :session (auth/user-session {:name "John Doe"} user-id)}]
          (is (= (-> congregation-model
                     (replace-in [:congregation :id] (UUID. 0 1) cong-id)
                     (replace-in [:user :user/id] (UUID. 0 2) user-id))
                 (layout/model! request {:title "the title"}))))))))

(deftest page-test
  (testing "minimal data"
    (is (= (html/normalize-whitespace
            "Territory Bro

             🏠 Home
             User guide {fa-external-link-alt}
             News {fa-external-link-alt}
             🛟 Support

             Login

             Sorry, something went wrong 🥺
             Close")
           (html/visible-text
            (layout/page nil nil)))))

  (testing "top-level navigation, anonymous"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             User guide {fa-external-link-alt}
             News {fa-external-link-alt}
             🛟 Support

             Login

             Sorry, something went wrong 🥺
             Close

             the content")
           (html/visible-text
            (layout/page anonymous-model
              (h/html [:p "the content"]))))))

  (testing "top-level navigation, developer"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             User guide {fa-external-link-alt}
             News {fa-external-link-alt}
             🛟 Support

             Login
             Dev Login

             Sorry, something went wrong 🥺
             Close

             the content")
           (html/visible-text
            (layout/page developer-model
              (h/html [:p "the content"]))))))

  (testing "top-level navigation, logged in"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             User guide {fa-external-link-alt}
             News {fa-external-link-alt}
             🛟 Support

             {fa-user-large} John Doe
             Logout

             Sorry, something went wrong 🥺
             Close

             the content")
           (html/visible-text
            (layout/page logged-in-model
              (h/html [:p "the content"]))))))

  ;; TODO: logged in vs anonymous

  (testing "congregation-level navigation"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             the congregation
             📍 Territories
             🖨️ Printouts
             ⚙️ Settings
             🛟 Support

             {fa-user-large} John Doe
             Logout

             Sorry, something went wrong 🥺
             Close

             the content")
           (html/visible-text
            (layout/page congregation-model
              (h/html [:p "the content"])))))))


;;;; Components and helpers

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
