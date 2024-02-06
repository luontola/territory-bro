;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout-test
  (:require [clojure.test :refer :all]
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
