;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.user-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.user :as user])
  (:import (java.util UUID)))

(use-fixtures :once db-fixture)

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

      (testing "list users"
        (is (= ["user1" "user2"]
               (->> (user/get-users conn)
                    (map :user/subject)
                    (sort)))))

      (testing "find users by IDs"
        (is (= ["user1"]
               (->> (user/get-users conn {:ids [user-id]})
                    (map :user/subject)
                    (sort))))
        (is (= ["user1" "user2"]
               (->> (user/get-users conn {:ids [user-id unrelated-user-id]})
                    (map :user/subject)
                    (sort)))))

      (testing "find user by ID"
        (is (= user-id (:user/id (user/get-by-id conn user-id))))
        (is (nil? (user/get-by-id conn (UUID/randomUUID)))
            "not found"))

      (testing "find user by subject"
        (is (= user-id (:user/id (user/get-by-subject conn "user1"))))
        (is (nil? (user/get-by-subject conn "no-such-user"))
            "not found"))

      (testing "did not accidentally change unrelated users"
        (is (= unrelated-user (user/get-by-id conn unrelated-user-id)))))))
