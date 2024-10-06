;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.documentation-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.ui.documentation-page :as documentation-page]))

(deftest view-test
  (testing "renders the markdown page"
    (is (str/includes? (documentation-page/view)
                       "<h2><a href=\"#getting-started\" id=\"getting-started\">Getting Started</a></h2>"))))
