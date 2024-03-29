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

  (testing "select tags are replaced with the selected option's visible text"
    ;; TODO: do we need real html parsing, not just regex? consider enlive/html-snippet
    #_(is (= "Option 1" (html/visible-text "<select name=\"foo\"><option value=\"opt1\">Option 1</option><option value=\"opt2\">Option 2</option></select>"))
          "the first element is selected by default")
    (is (= "Option 2" (html/visible-text "<select name=\"foo\"><option value=\"opt1\">Option 1</option><option value=\"opt2\" selected>Option 2</option></select>"))
        "explicitly selected option"))

  (testing "Font Awesome icons are replaced with the icon name"
    (is (= "{fa-share-nodes}" (html/visible-text "<i class=\"fa-solid fa-share-nodes\"></i>"))
        "icon style solid")
    (is (= "{fa-share-nodes}" (html/visible-text "<i class=\"fa-regular fa-share-nodes\"></i>"))
        "icon style regular")
    (is (= "{fa-bell}" (html/visible-text "<i class=\"fa-bell\"></i>"))
        "icon style missing")
    (is (= "{fa-share-nodes}" (html/visible-text "<i attr1=\"\" class=\"fa-solid fa-share-nodes\" attr2=\"\"></i>"))
        "more attributes")
    (is (= "{fa-language}" (html/visible-text "<i class=\"fa-solid fa-language Layout-module__languageSelectionIcon--VcMOP\"></i>"))
        "more classes")
    (is (= "foo {fa-share-nodes} bar" (html/visible-text "foo<i class=\"fa-solid fa-share-nodes\"></i>bar"))
        "add spacing around icon")
    (is (= "" (html/visible-text "<i class=\"whatever\"></i>"))
        "not an icon"))

  (testing "hides template elements"
    (is (= "" (html/visible-text "<template>stuff</template>")))
    (is (= "" (html/visible-text "<template id=\"xyz\">stuff</template>"))))

  (testing "normalizes whitespace"
    (is (= "one two" (html/visible-text "  <p>one</p>\n<br><p>two</p>\n  "))))

  (testing "works for raw hiccup strings"
    (is (= "stuff" (html/visible-text (h/raw "<p>stuff</p>"))))))
