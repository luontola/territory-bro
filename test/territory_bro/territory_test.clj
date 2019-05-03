;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.territory-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.territory :as territory]))

(use-fixtures :once db-fixture)

(deftest territories-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "the name")
          _ (congregation/use-schema conn cong-id)
          territory-id (territory/create-territory! conn {::territory/number "123"
                                                          ::territory/addresses "Street 1 A"
                                                          ::territory/subregion "Somewhere"
                                                          ::territory/location "MULTIPOLYGON(((30 20, 45 40, 10 40, 30 20)),((15 5, 40 10, 10 20, 5 10, 15 5)))"})]

      (testing "create new territory"
        (is territory-id))

      (testing "get territory by ID"
        (is (= {::territory/id territory-id
                ::territory/number "123"
                ::territory/addresses "Street 1 A"
                ::territory/subregion "Somewhere"
                ::territory/location "MULTIPOLYGON(((30 20,45 40,10 40,30 20)),((15 5,40 10,10 20,5 10,15 5)))"}
               (territory/get-by-id conn territory-id))))

      (testing "list territories"
        (is (= ["123"]
               (->> (territory/get-territories conn)
                    (map ::territory/number)
                    (sort))))))))
