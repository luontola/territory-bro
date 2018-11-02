; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.routes-test
  (:require [clojure.test :refer :all]
            [territory-bro.routes :refer :all]))

(deftest find-tenant-test
  (let [env {:tenant {:foo nil
                      :bar nil}}]
    (testing "valid tenant"
      (is (= :foo (find-tenant {:headers {"x-tenant" "foo"}} env)))
      (is (= :bar (find-tenant {:headers {"x-tenant" "bar"}} env))))
    (testing "invalid tenant"
      (is (= nil (find-tenant {:headers {"x-tenant" "gazonk"}} env))))
    (testing "unspecified tenant"
      (is (= nil (find-tenant {:headers {}} env))))))
