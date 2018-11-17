; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions-test
  (:require [clojure.test :refer :all]
            [territory-bro.permissions :as perm]))

(deftest user-permissions-test
  (let [env {:super-admin "superadmin"
             :tenant {:congregation1 {:admins #{"admin1a" "admin1b"}}
                      :congregation2 {:admins #{"admin2"}}}}]

    (testing "unrecognized user"
      (is (= {}
             (perm/user-permissions {:sub "user"} env))))

    (testing "congregation admin"
      (is (= {:congregation1 #{:view-territories}}
             (perm/user-permissions {:sub "admin1a"} env))))

    (testing "super admin"
      (is (= {:congregation1 #{:view-territories}
              :congregation2 #{:view-territories}}
             (perm/user-permissions {:sub "superadmin"} env))))))

(deftest visible-congregations-test
  (is (= [:congregation1 :congregation2]
         (perm/visible-congregations {:congregation1 #{:view-territories}
                                      :congregation2 #{}})))
  (is (= nil
         (perm/visible-congregations {}))))

(deftest can-view-territories?-test
  (is (true? (perm/can-view-territories? :congregation1 {:congregation1 #{:view-territories}})))
  (is (false? (perm/can-view-territories? :congregation1 {:congregation1 #{}})))
  (is (false? (perm/can-view-territories? :congregation1 {}))))
