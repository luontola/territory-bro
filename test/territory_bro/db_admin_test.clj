;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin-test
  (:require [clojure.test :refer :all]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.testutil :as testutil])
  (:import (java.util UUID)))

(defn- apply-events [events]
  (testutil/apply-events db-admin/projection events))

(deftest projection-test
  (testing "congregation created"
    (let [cong-id (UUID. 0 1)
          user-id (UUID. 0 2)
          events [{:event/type :congregation.event/congregation-created
                   :event/version 1
                   :congregation/id cong-id
                   :congregation/name "Cong1 Name"
                   :congregation/schema-name "cong1_schema"}]
          expected {::db-admin/congregations {cong-id {:congregation/schema-name "cong1_schema"}}
                    ::db-admin/pending-tenants #{{:congregation/schema-name "cong1_schema"}}}]
      (is (= expected (apply-events events)))

      (testing "> GIS user created"
        (let [events (conj events {:event/type :congregation.event/gis-user-created
                                   :event/version 1
                                   :congregation/id cong-id
                                   :user/id user-id
                                   :gis-user/username "username123"
                                   :gis-user/password "password123"})
              expected (merge expected
                              {::db-admin/pending-gis-users
                               {[cong-id user-id] {::db-admin/desired-state :present
                                                   :gis-user/username "username123"
                                                   :gis-user/password "password123"
                                                   :congregation/schema-name "cong1_schema"}}})]
          (is (= expected (apply-events events)))

          (testing "> GIS user is present"
            (let [events (conj events {:event/type :db-admin.event/gis-user-is-present
                                       :event/version 1
                                       :event/transient? true
                                       :congregation/id cong-id
                                       :user/id user-id
                                       :gis-user/username "username123"})
                  expected (merge expected
                                  {::db-admin/pending-gis-users {}})]
              (is (= expected (apply-events events)))))

          (testing "> GIS user deleted"
            (let [events (conj events {:event/type :congregation.event/gis-user-deleted
                                       :event/version 1
                                       :congregation/id cong-id
                                       :user/id user-id
                                       :gis-user/username "username123"})
                  expected (merge expected
                                  {::db-admin/pending-gis-users
                                   {[cong-id user-id] {::db-admin/desired-state :absent
                                                       :gis-user/username "username123"
                                                       :congregation/schema-name "cong1_schema"}}})]
              (is (= expected (apply-events events)))

              (testing "> GIS user is present"
                (let [events (conj events {:event/type :db-admin.event/gis-user-is-present
                                           :event/version 1
                                           :event/transient? true
                                           :congregation/id cong-id
                                           :user/id user-id
                                           :gis-user/username "username123"})]
                  (is (= expected (apply-events events))))))))))))
