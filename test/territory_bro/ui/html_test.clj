;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.html-test
  (:require [clojure.test :refer :all]
            [hiccup2.core :as h]
            [territory-bro.ui.html :as html]))

(deftest visible-text-test
  (testing "empty input"
    (is (= "" (html/visible-text "")))
    (is (= "" (html/visible-text nil))))

  (testing "replaces html tags with whitespace"
    (is (= "one two" (html/visible-text "<p>one</p><p>two</p>"))))

  (testing "input tags are replaced with their visible text"
    (is (= "the text" (html/visible-text "<input type=\"text\" value=\"the text\" required>"))))

  (testing "normalizes whitespace"
    (is (= "one two" (html/visible-text "  <p>one</p>\n<br><p>two</p>\n  "))))

  (testing "works for raw hiccup strings"
    (is (= "stuff" (html/visible-text (h/raw "<p>stuff</p>"))))))
