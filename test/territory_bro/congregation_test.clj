;; Copyright © 2015-2018 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.test :refer :all]
            [territory-bro.congregation :refer :all]))

; TODO
(deftest my-congregations-test
  (testing "lists congregations to which the user has access")
  (testing "hides congregations to which the user has no access")
  (testing "superadmin can access all congregations"))
