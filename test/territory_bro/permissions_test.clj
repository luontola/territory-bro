;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions-test
  (:require [clojure.test :refer :all]
            [territory-bro.permissions :as permissions])
  (:import (java.util UUID)))

(deftest changing-permissions-test
  (let [user-id (UUID. 0 1)
        user-id2 (UUID. 0 2)
        cong-id (UUID. 0 10)
        cong-id2 (UUID. 0 20)
        resource-id (UUID. 0 100)]

    (testing "granting,"
      (testing "one permission"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])))))

      (testing "many permissions"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true
                                                             :bar true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:bar cong-id])))))

      (testing "many congregations"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true}
                                                    cong-id2 {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:foo cong-id2])))))

      (testing "many users"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true}}
                                           user-id2 {cong-id {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id2 [:foo cong-id])))))

      (testing "nested permissions"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true
                                                             resource-id {:bar true}
                                                             :gazonk true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:bar cong-id resource-id])
                   (permissions/grant user-id [:gazonk cong-id]))))))

    (testing "revoking,"
      (testing "some permissions"
        (is (= {::permissions/permissions {user-id {cong-id {:bar true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:bar cong-id])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "some congregations"
        (is (= {::permissions/permissions {user-id {cong-id2 {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:foo cong-id2])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "some users"
        (is (= {::permissions/permissions {user-id2 {cong-id {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id2 [:foo cong-id])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "nested permissions"
        (is (= {::permissions/permissions {user-id {cong-id {:bar true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id resource-id])
                   (permissions/grant user-id [:bar cong-id])
                   (permissions/revoke user-id [:foo cong-id resource-id])))))

      (testing "all permissions"
        (is (= {}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/revoke user-id [:foo cong-id]))))))))

(deftest checking-permissions-test
  (let [user-id (UUID. 0 1)
        user-id2 (UUID. 0 2)
        cong-id (UUID. 0 10)
        cong-id2 (UUID. 0 20)
        resource-id (UUID. 0 100)]

    (let [state (permissions/grant nil user-id [:foo cong-id])]
      (testing "exact permission"
        (is (true? (permissions/allowed? state user-id [:foo cong-id]))))

      (testing "different permission"
        (is (false? (permissions/allowed? state user-id [:bar cong-id]))))

      (testing "different congregation"
        (is (false? (permissions/allowed? state user-id [:foo cong-id2]))))

      (testing "different user"
        (is (false? (permissions/allowed? state user-id2 [:foo cong-id])))))

    (testing "broader permission implies nested permissions"
      (is (true? (-> nil
                     (permissions/grant user-id [:foo cong-id])
                     (permissions/allowed? user-id [:foo cong-id resource-id]))))
      (is (true? (-> nil
                     (permissions/grant user-id [:foo])
                     (permissions/allowed? user-id [:foo cong-id resource-id])))))

    (testing "nested permission doesn't imply broader permission"
      (is (false? (-> nil
                      (permissions/grant user-id [:foo cong-id resource-id])
                      (permissions/allowed? user-id [:foo cong-id]))))
      (is (false? (-> nil
                      (permissions/grant user-id [:foo cong-id resource-id])
                      (permissions/allowed? user-id [:foo])))))))
