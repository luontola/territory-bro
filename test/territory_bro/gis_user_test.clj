;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.user :as user]))

(use-fixtures :once db-fixture)

(deftest gis-users-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "cong1")
          cong-id2 (congregation/create-congregation! conn "cong2")
          user-id (user/save-user! conn "user1" {})
          user-id2 (user/save-user! conn "user2" {})]

      (gis-user/create-gis-user! conn cong-id user-id)
      (gis-user/create-gis-user! conn cong-id2 user-id2)
      (testing "create & get user"
        (let [user (gis-user/get-gis-user conn cong-id user-id)]
          (is (::gis-user/username user))
          (is (= 50 (count ((::gis-user/password user)))))))

      (testing "get all users"
        (is (= #{[cong-id user-id]
                 [cong-id2 user-id2]}
               (->> (gis-user/get-gis-users conn)
                    (map (juxt ::gis-user/congregation ::gis-user/user))
                    (into #{})))))

      (testing "can login to the database") ; TODO

      (testing "can view the congregation schema") ; TODO

      (testing "cannot view the master schema") ; TODO

      (testing "cannot view the public schema") ; TODO

      (gis-user/delete-gis-user! conn cong-id user-id)
      (testing "delete user"
        (is (nil? (gis-user/get-gis-user conn cong-id user-id)))
        (is (gis-user/get-gis-user conn cong-id2 user-id2)
            "should not delete unrelated users"))

      (testing "cannot login to database after user is deleted")))) ; TODO

(deftest generate-password-test
  (let [a (gis-user/generate-password 10)
        b (gis-user/generate-password 10)]
    (is (= 10 (count a) (count b)))
    (is (not (= a b)))))

(deftest secret-test
  (let [secret (gis-user/secret "foo")]

    (testing "hides the secret when printed normally"
      (is (not (str/includes? (str secret) "foo")))
      (is (not (str/includes? (pr-str secret) "foo"))))

    (testing "exposes the secret when invoked as a function"
      (is (= "foo" (secret))))))
