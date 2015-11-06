(ns territory-bro.test.db.core
  (:require [territory-bro.db.core :as db]
            [territory-bro.db.migrations :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [conman.core :refer [with-transaction]]
            [environ.core :refer [env]]))

(use-fixtures
  :once
  (fn [f]
    (db/connect!)
    (migrations/migrate ["migrate"])
    (f)))

#_(deftest test-users
  (with-transaction [t-conn db/*conn*]
    (jdbc/db-set-rollback-only! t-conn)
    (is (= 1 (db/create-user!
               {:id         "1"
                :first_name "Sam"
                :last_name  "Smith"
                :email      "sam.smith@example.com"
                :pass       "pass"})))
    (is (= [{:id         "1"
             :first_name "Sam"
             :last_name  "Smith"
             :email      "sam.smith@example.com"
             :pass       "pass"
             :admin      nil
             :last_login nil
             :is_active  nil}]
           (db/get-user {:id "1"})))))
