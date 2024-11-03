(ns territory-bro.ui.documentation-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.ui.documentation-page :as documentation-page]))

(deftest view-test
  (testing "renders markdown content"
    (is (str/includes? (documentation-page/view)
                       "<h2><a href=\"#getting-started\" id=\"getting-started\">Getting Started</a></h2>"))))
