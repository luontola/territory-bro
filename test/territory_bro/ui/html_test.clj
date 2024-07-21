;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.html-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [hiccup2.core :as h]
            [territory-bro.ui.html :as html]))

(deftest visible-text-test
  (testing "empty input"
    (is (= "" (html/visible-text "")))
    (is (= "" (html/visible-text nil))))

  (testing "replaces html tags with whitespace"
    (is (= "one two" (html/visible-text "<p>one</p><p>two</p>"))))

  (testing "input tags are replaced with their visible text"
    (is (= "[the text]" (html/visible-text "<input type=\"text\" value=\"the text\" required>")))
    (is (= "x [Value] y" (html/visible-text "x<input value=\"Value\">y"))
        "spacing before and after element")
    (is (= "[]" (html/visible-text "<input type=\"text\">"))
        "without explicit value field"))

  (testing "select tags are replaced with the selected option's visible text"
    ;; TODO: do we need real html parsing, not just regex? consider enlive/html-snippet
    #_(is (= "[Option 1]" (html/visible-text "<select name=\"foo\"><option value=\"opt1\">Option 1</option><option value=\"opt2\">Option 2</option></select>"))
          "the first element is selected by default")
    (is (= "[Option 2]" (html/visible-text "<select name=\"foo\"><option value=\"opt1\">Option 1</option><option value=\"opt2\" selected>Option 2</option></select>"))
        "explicitly selected option")
    (is (= "x [Option] y" (html/visible-text "x<select><option selected>Option</option></select>y"))
        "spacing before and after element"))

  (testing "elements with the data-test-icon attribute are replaced with its value"
    (is (= "☑️" (html/visible-text "<input type=\"checkbox\" data-test-icon=\"☑️\" checked value=\"true\">")))
    (is (= "x 🟢 y z" (html/visible-text "x<div data-test-icon=\"🟢\">y</div>z"))
        "spacing before, inside and after element"))

  (testing "hides template elements"
    (is (= "" (html/visible-text "<template>stuff</template>")))
    (is (= "" (html/visible-text "<template id=\"xyz\">stuff</template>"))))

  (testing "replaces HTML character entities"
    (is (= "1 000" (html/visible-text "1&nbsp;000")))
    (is (= "<" (html/visible-text "&lt;")))
    (is (= ">" (html/visible-text "&gt;")))
    (is (= "&" (html/visible-text "&amp;")))
    (is (= "\"" (html/visible-text "&quot;")))
    (is (= "'" (html/visible-text "&apos;"))))

  (testing "inline elements will not add spacing to text"
    (is (= "xyz"
           (html/visible-text
            (h/html
             "x"
             [:a [:abbr [:b [:big [:cite [:code [:em [:i [:small [:span [:strong [:tt "y"]]]]]]]]]]]]
             "z")))))

  (testing "normalizes whitespace"
    (is (= "one two" (html/visible-text "  <p>one</p>\n<br><p>two</p>\n  "))))

  (testing "works for raw hiccup strings"
    (is (= "stuff" (html/visible-text (h/raw "<p>stuff</p>"))))))

(deftest public-resources-test
  (testing "regular files are mapped as-is"
    (is (= "/index.html" (get html/public-resources "/index.html"))))

  (testing "content-hashed files are mapped using a wildcard"
    (let [path (get html/public-resources "/assets/crop-mark-*.svg")]
      (is (some? path))
      (is (some? (io/resource (str "public" path)))))))

(deftest inline-svg-test
  (testing "returns the SVG image"
    (let [svg (html/inline-svg "icons/info.svg")]
      (is (hiccup.util/raw-string? svg))
      (is (str/starts-with? svg "<svg"))
      (is (str/includes? svg " class=\"svg-inline--fa\""))
      (is (str/includes? svg " data-test-icon=\"{info.svg}\""))
      (is (str/includes? svg "/>")
          "emits XML with self-closing tags, instead of HTML")))

  (testing "supports custom attributes"
    (is (str/includes? (html/inline-svg "icons/info.svg" {:foo "bar"})
                       " foo=\"bar\"")
        "known at compile time")
    (is (str/includes? (html/inline-svg "icons/info.svg" {:foo (str/upper-case "bar")})
                       " foo=\"BAR\"")
        "dynamically computed")
    (let [svg (html/inline-svg "icons/info.svg" {:foo 1, :bar 2})]
      (is (and (str/includes? svg " foo=\"1\"")
               (str/includes? svg " bar=\"2\""))
          "multiple attributes")))

  (testing "supports extra CSS classes"
    (is (str/includes? (html/inline-svg "icons/info.svg" {:class "custom-class"})
                       " class=\"svg-inline--fa custom-class\"")))

  (testing "supports style attribute as a map"
    (is (str/includes? (html/inline-svg "icons/info.svg"
                                        {:style {:font-size "1.25em"
                                                 :vertical-align "middle"}})
                       " style=\"font-size:1.25em;vertical-align:middle;\"")))

  (testing "supports titles using the SVG <title> element"
    (let [svg (html/inline-svg "icons/info.svg" {:title "The Title"})]
      (is (str/includes? svg "<title>The Title</title>"))
      (is (not (str/includes? svg "title="))
          "should not add a title attribute - they do nothing in SVG")))

  (testing "error: file not found"
    (is (nil? (html/inline-svg "no-such-file")))))
