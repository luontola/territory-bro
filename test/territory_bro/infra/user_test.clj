;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.infra.user-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.user :as user]
            [territory-bro.test.fixtures :refer [db-fixture]]
            [territory-bro.test.testutil :refer [re-equals thrown-with-msg?]])
  (:import (java.util UUID)
           (territory_bro ValidationException)))

(use-fixtures :once db-fixture)

(defn subjects [users]
  (->> users
       (map :user/subject)
       (sort)))

(deftest users-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [user-id (user/save-user! conn "user1" {:name "User 1"})
          unrelated-user-id (user/save-user! conn "user2" {:name "User 2"})
          unrelated-user (user/get-by-id conn unrelated-user-id)]

      (testing "create new user"
        (is user-id)
        (is (= {:user/id user-id
                :user/subject "user1"
                :user/attributes {:name "User 1"}}
               (user/get-by-id conn user-id))))

      (testing "update existing user"
        (is (= user-id
               (user/save-user! conn "user1" {:name "new name"}))
            "should return same ID as before")
        (is (= {:user/id user-id
                :user/subject "user1"
                :user/attributes {:name "new name"}}
               (user/get-by-id conn user-id))
            "should update attributes"))

      (testing "subject is not repeated in the user attributes, but all other attributes are kept"
        (let [user3 {:sub "user3"
                     :name "User 3"
                     :stuff "another attribute"}
              user-id3 (user/save-user! conn (:sub user3) user3)]
          (is (= {:user/id user-id3
                  :user/subject "user3"
                  :user/attributes {:name "User 3"
                                    :stuff "another attribute"}}
                 (user/get-by-id conn user-id3)))))

      (testing "list all users"
        (is (= ["user1" "user2" "user3"]
               (subjects (user/get-users conn)))))

      (testing "find users by IDs"
        (is (= ["user1"]
               (subjects (user/get-users conn {:ids [user-id]}))))
        (is (= ["user1" "user2"]
               (subjects (user/get-users conn {:ids [user-id unrelated-user-id]}))))
        (is (= []
               (subjects (user/get-users conn {:ids []}))))
        (is (= []
               (subjects (user/get-users conn {:ids nil})))))

      (testing "find user by ID"
        (is (= user-id (:user/id (user/get-by-id conn user-id))))
        (is (nil? (user/get-by-id conn (UUID/randomUUID)))
            "not found"))

      (testing "find users by subjects"
        (is (= ["user1"]
               (subjects (user/get-users conn {:subjects ["user1"]}))))
        (is (= ["user1" "user2"]
               (subjects (user/get-users conn {:subjects ["user1" "user2"]}))))
        (is (= []
               (subjects (user/get-users conn {:subjects []}))))
        (is (= []
               (subjects (user/get-users conn {:subjects nil})))))

      (testing "find user by subject"
        (is (= user-id (:user/id (user/get-by-subject conn "user1"))))
        (is (nil? (user/get-by-subject conn "no-such-user"))
            "not found"))

      (testing "did not accidentally change unrelated users"
        (is (= unrelated-user (user/get-by-id conn unrelated-user-id)))))))

(deftest check-user-exists-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [user-id (user/save-user! conn "user1" {:name "User 1"})]

      (testing "exists"
        (is (nil? (user/check-user-exists conn user-id))))

      (testing "doesn't exist"
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:no-such-user #uuid \"00000000-0000-0000-0000-000000000666\"]]")
             (user/check-user-exists conn (UUID. 0 0x666))))))))
