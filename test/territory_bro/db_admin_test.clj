;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin-test
  (:require [clojure.test :refer :all]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.testutil :as testutil])
  (:import (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def cong-created-event {:event/type :congregation.event/congregation-created
                         :event/version 1
                         :congregation/id cong-id
                         :congregation/name "Cong1 Name"
                         :congregation/schema-name "cong1_schema"})
(def user-created-event {:event/type :congregation.event/gis-user-created
                         :event/version 1
                         :congregation/id cong-id
                         :user/id user-id
                         :gis-user/username "username123"
                         :gis-user/password "password123"})
(def user-deleted-event {:event/type :congregation.event/gis-user-deleted
                         :event/version 1
                         :congregation/id cong-id
                         :user/id user-id
                         :gis-user/username "username123"})
(def schema-is-present-event {:event/type :db-admin.event/gis-schema-is-present
                              :event/version 1
                              :event/transient? true
                              :congregation/id cong-id
                              :congregation/schema-name "cong1_schema"})
(def user-is-present-event {:event/type :db-admin.event/gis-user-is-present
                            :event/version 1
                            :event/transient? true
                            :congregation/id cong-id
                            :user/id user-id
                            :gis-user/username "username123"})
(def user-is-absent-event {:event/type :db-admin.event/gis-user-is-absent
                           :event/version 1
                           :event/transient? true
                           :congregation/id cong-id
                           :user/id user-id
                           :gis-user/username "username123"})

(defn- apply-events [events]
  (testutil/apply-events db-admin/projection events))

(deftest projection-test
  (testing "congregation created"
    (let [events [cong-created-event]
          expected {::db-admin/congregations {cong-id {:congregation/id cong-id
                                                       :congregation/schema-name "cong1_schema"}}
                    ::db-admin/pending-schemas #{{:congregation/id cong-id
                                                  :congregation/schema-name "cong1_schema"}}}]
      (is (= expected (apply-events events)))

      (testing "> schema is present"
        (let [events (conj events schema-is-present-event)
              expected (merge expected {::db-admin/pending-schemas #{}})]
          (is (= expected (apply-events events)))))

      (testing "> GIS user created"
        (let [events (conj events user-created-event)
              expected (merge expected
                              {::db-admin/pending-gis-users
                               {[cong-id user-id] {::db-admin/desired-state :present
                                                   :gis-user/username "username123"
                                                   :gis-user/password "password123"
                                                   :congregation/schema-name "cong1_schema"}}})]
          (is (= expected (apply-events events)))

          (testing "> GIS user is present"
            (let [events (conj events user-is-present-event)
                  expected (merge expected {::db-admin/pending-gis-users {}})]
              (is (= expected (apply-events events)))))

          (testing "> GIS user is absent (undesired)"
            (let [events (conj events user-is-absent-event)]
              (is (= expected (apply-events events))
                  "no change")))

          (testing "> GIS user deleted"
            (let [events (conj events user-deleted-event)
                  expected (merge expected
                                  {::db-admin/pending-gis-users
                                   {[cong-id user-id] {::db-admin/desired-state :absent
                                                       :gis-user/username "username123"
                                                       :congregation/schema-name "cong1_schema"}}})]
              (is (= expected (apply-events events)))

              (testing "> GIS user is present (undesired)"
                (let [events (conj events user-is-present-event)]
                  (is (= expected (apply-events events))
                      "no change")))

              (testing "> GIS user is absent"
                (let [events (conj events user-is-absent-event)
                      expected (merge expected {::db-admin/pending-gis-users {}})]
                  (is (= expected (apply-events events))))))))))))

(deftest process-test
  (let [*spy (atom [])
        spy-results (fn []
                      (let [results @*spy]
                        (reset! *spy [])
                        results))
        injections {:dispatch! (fn [event]
                                 (testutil/validate-test-events [event])
                                 (swap! *spy conj [:dispatch! event]))
                    :migrate-tenant-schema! (fn [schema]
                                              (swap! *spy conj [:migrate-tenant-schema! schema]))
                    :ensure-gis-user-present! (fn [schema]
                                                (swap! *spy conj [:ensure-gis-user-present! schema]))}]
    (testing "empty state"
      (db-admin/process-pending-changes! nil injections)
      (is (empty? (spy-results))))

    (testing "pending schema"
      (let [state (apply-events [cong-created-event])]
        (db-admin/process-pending-changes! state injections)
        (is (= [[:migrate-tenant-schema! "cong1_schema"]
                [:dispatch! schema-is-present-event]]
               (spy-results)))))

    (testing "pending GIS user creation"
      (let [state (apply-events [cong-created-event schema-is-present-event user-created-event])]
        (db-admin/process-pending-changes! state injections)
        (is (= [[:ensure-gis-user-present! {:username "username123"
                                            :password "password123"
                                            :schema "cong1_schema"}]
                [:dispatch! user-is-present-event]]
               (spy-results)))))))
