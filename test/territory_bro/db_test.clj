; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :refer [with-transaction]]
            [environ.core :refer [env]]
            [territory-bro.db :as db]
            [territory-bro.db.migrations :as migrations]))

(use-fixtures
 :once
 (fn [f]
   (db/connect!)
   (migrations/migrate ["migrate"])
   (f)))

#_(deftest test-users
    (with-transaction [t-conn db/*conn*]
                      (jdbc/db-set-rollback-only! t-conn)
                      (is (= 1 (db/create-user! {:id "1"
                                                 :first_name "Sam"
                                                 :last_name "Smith"
                                                 :email "sam.smith@example.com"
                                                 :pass "pass"})))
                      (is (= [{:id "1"
                               :first_name "Sam"
                               :last_name "Smith"
                               :email "sam.smith@example.com"
                               :pass "pass"
                               :admin nil
                               :last_login nil
                               :is_active nil}]
                             (db/get-user {:id "1"})))))
