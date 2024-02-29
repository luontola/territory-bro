;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout-test
  (:require [clojure.test :refer :all]
            [hiccup2.core :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.layout :as layout]))

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

(deftest page-test
  (testing "minimal data"
    (is (= (html/normalize-whitespace
            "Territory Bro
            
             🏠 Home
             User guide {fa-external-link-alt}
             News {fa-external-link-alt}
             🛟 Support

             Sorry, something went wrong 🥺
             Close")
           (html/visible-text
            (layout/page {:title nil
                          :congregation nil}
              "")))))

  (testing "top-level navigation"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro
            
             🏠 Home
             User guide {fa-external-link-alt}
             News {fa-external-link-alt}
             🛟 Support

             Sorry, something went wrong 🥺
             Close

             the content")
           (html/visible-text
            (layout/page {:title "the title"
                          :congregation nil}
              (h/html [:p "the content"]))))))

  (testing "congregation-level navigation"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             the congregation
             📍 Territories
             🖨️ Printouts
             ⚙️ Settings
             🛟 Support

             Sorry, something went wrong 🥺
             Close

             the content")
           (html/visible-text
            (layout/page {:title "the title"
                          :congregation {:id "123"
                                         :name "the congregation"}}
              (h/html [:p "the content"])))))))
