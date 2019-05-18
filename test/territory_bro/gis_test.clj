;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.gis :as gis]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.territory :as territory]))

(use-fixtures :once db-fixture)

(deftest gis-change-log-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "the name")
          _ (congregation/use-schema conn cong-id)]

      (testing "before making changes"
        (is (= [] (gis/get-gis-changes conn))))

      (testing "territory table change log"
        (let [territory-id (territory/create-territory! conn {::territory/number "123"
                                                              ::territory/addresses "Street 1 A"
                                                              ::territory/subregion "Somewhere"
                                                              ::territory/meta {:foo "bar", :gazonk 42}
                                                              ::territory/location "MULTIPOLYGON(((30 20, 45 40, 10 40, 30 20)),((15 5, 40 10, 10 20, 5 10, 15 5)))"})
              changes (gis/get-gis-changes conn)]
          (is (= 1 (count changes)))
          (is (= {:table "territory"
                  :new {:id (str territory-id)
                        :number "123"
                        :addresses "Street 1 A"
                        :subregion "Somewhere"
                        :meta {:foo "bar", :gazonk 42}
                        :location "MULTIPOLYGON(((30 20,45 40,10 40,30 20)),((15 5,40 10,10 20,5 10,15 5)))"}}
                 (-> (first changes)
                     (select-keys [:table :new]))))))
      ;; TODO: update and delete

      (testing "congregation_boundary table change log") ; TODO

      (testing "subregion table change log") ; TODO

      (testing "card_minimap_viewport table change log")))) ; TODO
