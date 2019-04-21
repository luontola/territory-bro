;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.user :as user])
  (:import (java.util UUID)))

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

    (let [congregations (congregation/get-unrestricted-congregations conn)]
      (testing "list congregations"
        (is (not (empty? congregations)))
        (is (contains? (set (map ::congregation/name congregations))
                       "the name")))
      (testing "get congregation by ID"
        (let [id (::congregation/id (first congregations))]
          (is (= (first congregations) (congregation/get-unrestricted-congregation conn id)))
          (is (nil? (congregation/get-unrestricted-congregation conn (UUID/randomUUID)))))))))

(deftest congregation-access-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "")
          unrelated-cong-id (congregation/create-congregation! conn "")
          user-id (user/create-user! conn "user" {})]

      (testing "cannot see congregations by default"
        (is (nil? (congregation/get-my-congregation conn cong-id user-id)))
        (is (empty? (congregation/get-my-congregations conn user-id))))

      (congregation/grant-access! conn cong-id user-id)
      (testing "can see congregations after granting access"
        (is (= cong-id (::congregation/id (congregation/get-my-congregation conn cong-id user-id))))
        (is (= [cong-id] (->> (congregation/get-my-congregations conn user-id)
                              (map ::congregation/id)))))
      (testing "list users"
        (is (= [user-id] (congregation/get-users conn cong-id)))
        (is (= [] (congregation/get-users conn unrelated-cong-id))
            "unrelated congregation"))

      (congregation/revoke-access! conn cong-id user-id)
      (testing "cannot see congregations after revoking access"
        (is (nil? (congregation/get-my-congregation conn cong-id user-id)))
        (is (empty? (congregation/get-my-congregations conn user-id))))

      (testing "superadmin can access all congregations")))) ; TODO
