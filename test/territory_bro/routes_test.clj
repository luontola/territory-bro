; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.routes-test
  (:require [clojure.test :refer :all]
            [territory-bro.routes :refer :all]))

(deftest find-tenant-test
  (let [tenants [:foo :bar]]
    (testing "valid tenant"
      (is (= :foo (find-tenant {:headers {"x-tenant" "foo"}} tenants)))
      (is (= :bar (find-tenant {:headers {"x-tenant" "bar"}} tenants))))
    (testing "invalid tenant"
      (is (= nil (find-tenant {:headers {"x-tenant" "gazonk"}} tenants))))
    (testing "unspecified tenant"
      (is (= nil (find-tenant {:headers {}} tenants))))))
