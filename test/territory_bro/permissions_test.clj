;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions-test
  (:require [clojure.test :refer :all]
            [territory-bro.permissions :as permissions])
  (:import (java.util UUID)))

(deftest permissions-model-test
  (let [user-id (UUID. 0 1)
        user-id2 (UUID. 0 2)
        cong-id (UUID. 0 10)
        cong-id2 (UUID. 0 20)]

    (testing "granting,"
      (testing "one permission"
        (is (= {::permissions/permissions {user-id {cong-id #{:foo}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])))))

      (testing "many permissions"
        (is (= {::permissions/permissions {user-id {cong-id #{:foo :bar}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:bar cong-id])))))

      (testing "many congregations"
        (is (= {::permissions/permissions {user-id {cong-id #{:foo}
                                                    cong-id2 #{:foo}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:foo cong-id2])))))

      (testing "many users"
        (is (= {::permissions/permissions {user-id {cong-id #{:foo}}
                                           user-id2 {cong-id #{:foo}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id2 [:foo cong-id]))))))

    (testing "revoking,"
      (testing "some permissions"
        (is (= {::permissions/permissions {user-id {cong-id #{:bar}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:bar cong-id])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "some congregations"
        (is (= {::permissions/permissions {user-id {cong-id2 #{:foo}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:foo cong-id2])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "some users"
        (is (= {::permissions/permissions {user-id2 {cong-id #{:foo}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id2 [:foo cong-id])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "all permissions"
        (is (= {}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/revoke user-id [:foo cong-id]))))))))
