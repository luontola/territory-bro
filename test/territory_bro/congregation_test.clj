;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.user :as user]))

(use-fixtures :once db-fixture)

(deftest congregations-test
  (db/with-db [conn {:isolation :read-committed}] ; creating the schema happens in another transaction
    (jdbc/db-set-rollback-only! conn)

    (testing "no congregations"
      (is (= [] (congregation/get-unrestricted-congregations conn))))

    (testing "create congregation"
      (let [id (congregation/create-congregation! conn "the name")
            congregation (congregation/get-unrestricted-congregation conn id)]
        (is id)
        (is (= id (::congregation/id congregation)))
        (is (= "the name" (::congregation/name congregation)))
        (is (contains? (set (db/get-schemas conn))
                       (::congregation/schema-name congregation))
            "should create congregation schema")))

    (testing "list congregations"
      (let [congregations (congregation/get-unrestricted-congregations conn)]
        (is (not (empty? congregations)))
        (is (contains? (set (map ::congregation/name congregations))
                       "the name"))))

    (testing "hides congregations to which the user has no access") ; TODO
    (testing "superadmin can access all congregations"))) ; TODO

(deftest congregation-membership-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "")
          unrelated-cong-id (congregation/create-congregation! conn "")
          user-id (user/create-user! conn "user" {})]

      (testing "cannot see congregations before joining them"
        (is (nil? (congregation/get-my-congregation conn cong-id user-id)))
        (is (empty? (congregation/get-my-congregations conn user-id))))

      (congregation/add-member! conn cong-id user-id)
      (testing "can see congregations after joining them"
        (is (= cong-id (::congregation/id (congregation/get-my-congregation conn cong-id user-id))))
        (is (= [cong-id] (->> (congregation/get-my-congregations conn user-id)
                              (map ::congregation/id)))))

      (testing "list congregation members"
        (is (= [user-id] (congregation/get-members conn cong-id)))
        (is (= [] (congregation/get-members conn unrelated-cong-id))
            "unrelated congregation"))

      (congregation/remove-member! conn cong-id user-id)
      (testing "cannot see congregations after leaving them"
        (is (nil? (congregation/get-my-congregation conn cong-id user-id)))
        (is (empty? (congregation/get-my-congregations conn user-id)))))))
