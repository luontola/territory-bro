(ns territory-bro.ui.privacy-policy-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.ui.privacy-policy-page :as privacy-policy-page]))

(deftest view-test
  (let [html (str (privacy-policy-page/view))]
    (testing "renders markdown content"
      (is (str/includes? html "What data do we collect?</a></h2>")))

    (testing "email address uses an SVG image for the @ sign to avoid spam"
      (is (str/includes? html "contact us by email: privacy-policy<svg ")))

    (testing "says when the privacy policy was last updated, calculated dynamically from file modification time"
      (is (str/includes? html "last updated on 20")))))
