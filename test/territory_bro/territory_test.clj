;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.territory-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture event-actor-fixture]]
            [territory-bro.projections :as projections]
            [territory-bro.territory :as territory]
            [territory-bro.testdata :as testdata]))

(use-fixtures :once (join-fixtures [db-fixture event-actor-fixture]))

(deftest territories-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "the name")
          _ (congregation/use-schema conn (projections/current-state conn) cong-id)
          territory-id (territory/create-territory! conn {:territory/number "123"
                                                          :territory/addresses "Street 1 A"
                                                          :territory/subregion "Somewhere"
                                                          :territory/meta {:foo "bar", :gazonk 42}
                                                          :territory/location testdata/wkt-multi-polygon})]

      (testing "create new territory"
        (is territory-id))

      (testing "get territory by ID"
        (is (= {:territory/id territory-id
                :territory/number "123"
                :territory/addresses "Street 1 A"
                :territory/subregion "Somewhere"
                :territory/meta {:foo "bar", :gazonk 42}
                :territory/location testdata/wkt-multi-polygon}
               (territory/get-by-id conn territory-id))))

      (testing "get territories by IDs"
        (is (= [territory-id]
               (->> (territory/get-territories conn {:ids [territory-id]})
                    (map :territory/id))))
        (is (= []
               (->> (territory/get-territories conn {:ids []})
                    (map :territory/id))))
        (is (= []
               (->> (territory/get-territories conn {:ids nil})
                    (map :territory/id)))))

      (testing "list territories"
        (is (= ["123"]
               (->> (territory/get-territories conn)
                    (map :territory/number)
                    (sort))))))))
