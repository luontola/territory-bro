;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.region-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture event-actor-fixture]]
            [territory-bro.region :as region]
            [territory-bro.testdata :as testdata]))

(use-fixtures :once (join-fixtures [db-fixture event-actor-fixture]))

(deftest regions-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "the name")
          _ (congregation/use-schema conn cong-id)]

      (testing "create & list congregation boundaries"
        (let [id (region/create-congregation-boundary! conn testdata/wkt-multi-polygon)]
          (is (= [{:region/id id
                   :region/location testdata/wkt-multi-polygon}]
                 (region/get-congregation-boundaries conn)))))

      (testing "create & list subregions"
        (let [id (region/create-subregion! conn "the name" testdata/wkt-multi-polygon)]
          (is (= [{:region/id id
                   :region/name "the name"
                   :region/location testdata/wkt-multi-polygon}]
                 (region/get-subregions conn)))))

      (testing "create & list card minimap viewports"
        (let [id (region/create-card-minimap-viewport! conn testdata/wkt-polygon)]
          (is (= [{:region/id id
                   :region/location testdata/wkt-polygon}]
                 (region/get-card-minimap-viewports conn))))))))
