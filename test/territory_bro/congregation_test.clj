;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.config :as config]))

(use-fixtures :once db-fixture)

(deftest congregations-test
  (db/with-db [conn {}]

    (testing "no congregations"
      (is (= [] (congregation/query! conn :get-congregations))))

    (testing "create congregation"
      (let [id (congregation/create-congregation! conn "the name")
            congregation (congregation/get-congregation conn id)]
        (is id)
        (is (= id (::congregation/id congregation)))
        (is (= "the name" (::congregation/name congregation)))
        (is (= [] (jdbc/query conn [(str "select * from " (::congregation/schema-name congregation) ".territory")]))
            "should create congregation schema")))

    (testing "list congregations"
      (let [congregations (congregation/get-congregations conn)]
        (is (not (empty? congregations)))
        (is (contains? (set (map ::congregation/name congregations))
                       "the name"))))

    (testing "hides congregations to which the user has no access") ; TODO
    (testing "superadmin can access all congregations"))) ; TODO
