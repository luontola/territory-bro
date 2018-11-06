; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.authenticaton-test
  (:require [clojure.test :refer :all]
            [territory-bro.authentication :refer :all]))

(deftest super-admin-test
  (let [env {:super-admin "super"}]
    (testing "not authenticated"
      (is (false? (super-admin? nil env))))
    (testing "authenticated as normal user"
      (is (false? (super-admin? {:sub "normal"} env))))
    (testing "authenticated as super admin"
      (is (true? (super-admin? {:sub "super"} env)))))
  (testing "no super admin defined"
    (let [env {:super-admin nil}]
      (is (false? (super-admin? nil env)))
      (is (false? (super-admin? {:sub "normal"} env))))))
