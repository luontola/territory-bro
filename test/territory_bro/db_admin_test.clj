;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin-test
  (:require [clojure.test :refer :all]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.spy :as spy]
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

(defn- generate-commands [events]
  (->> events apply-events db-admin/generate-commands))

(deftest projection-test
  (testing "congregation created"
    (let [events [cong-created-event]]
      (is (= [{:command/type :db-admin.command/migrate-tenant-schema
               :congregation/id cong-id
               :congregation/schema-name "cong1_schema"}]
             (generate-commands events)))

      (testing "> schema is present"
        (let [events (conj events schema-is-present-event)]
          (is (empty? (generate-commands events)))))

      (testing "> GIS user created"
        (let [events (conj events user-created-event)]
          ;; TODO: these tests should not care about tenant schema migration
          (is (= [{:command/type :db-admin.command/migrate-tenant-schema
                   :congregation/id cong-id
                   :congregation/schema-name "cong1_schema"}
                  {:command/type :db-admin.command/ensure-gis-user-present
                   :congregation/id cong-id
                   :congregation/schema-name "cong1_schema"
                   :user/id user-id
                   :gis-user/password "password123"
                   :gis-user/username "username123"}]
                 (generate-commands events)))

          (testing "> GIS user is present"
            (let [events (conj events user-is-present-event)]
              (is (= [{:command/type :db-admin.command/migrate-tenant-schema
                       :congregation/id cong-id
                       :congregation/schema-name "cong1_schema"}]
                     (generate-commands events)))))

          (testing "> GIS user is absent (undesired)"
            (let [events (conj events user-is-absent-event)]
              (is (= [{:command/type :db-admin.command/migrate-tenant-schema
                       :congregation/id cong-id
                       :congregation/schema-name "cong1_schema"}
                      {:command/type :db-admin.command/ensure-gis-user-present
                       :congregation/id cong-id
                       :congregation/schema-name "cong1_schema"
                       :user/id user-id
                       :gis-user/password "password123"
                       :gis-user/username "username123"}]
                     (generate-commands events)))))

          (testing "> GIS user deleted"
            (let [events (conj events user-deleted-event)]
              (is (= [{:command/type :db-admin.command/migrate-tenant-schema
                       :congregation/id cong-id
                       :congregation/schema-name "cong1_schema"}
                      {:command/type :db-admin.command/ensure-gis-user-absent
                       :congregation/id cong-id
                       :congregation/schema-name "cong1_schema"
                       :user/id user-id
                       :gis-user/username "username123"}]
                     (generate-commands events)))

              (testing "> GIS user is present (undesired)"
                (let [events (conj events user-is-present-event)]
                  (is (= [{:command/type :db-admin.command/migrate-tenant-schema
                           :congregation/id cong-id
                           :congregation/schema-name "cong1_schema"}
                          {:command/type :db-admin.command/ensure-gis-user-absent
                           :congregation/id cong-id
                           :congregation/schema-name "cong1_schema"
                           :user/id user-id
                           :gis-user/username "username123"}]
                         (generate-commands events)))))

              (testing "> GIS user is absent"
                (let [events (conj events user-is-absent-event)]
                  (is (= [{:command/type :db-admin.command/migrate-tenant-schema
                           :congregation/id cong-id
                           :congregation/schema-name "cong1_schema"}]
                         (generate-commands events))))))))))))

(deftest process-test
  (let [spy (spy/spy)
        injections {:dispatch! (spy/fn spy :dispatch! #(testutil/validate-test-events [%]))
                    :migrate-tenant-schema! (spy/fn spy :migrate-tenant-schema!)
                    :ensure-gis-user-present! (spy/fn spy :ensure-gis-user-present!)
                    :ensure-gis-user-absent! (spy/fn spy :ensure-gis-user-absent!)}]
    (testing "empty state"
      (db-admin/process-pending-changes! nil injections)
      (is (empty? (spy/read! spy))))

    (testing "pending schema"
      (let [state (apply-events [cong-created-event])]
        (db-admin/process-pending-changes! state injections)
        (is (= [[:migrate-tenant-schema! "cong1_schema"]
                [:dispatch! schema-is-present-event]]
               (spy/read! spy)))))

    (testing "pending GIS user creation"
      (let [state (apply-events [cong-created-event schema-is-present-event
                                 user-created-event])]
        (db-admin/process-pending-changes! state injections)
        (is (= [[:ensure-gis-user-present! {:username "username123"
                                            :password "password123"
                                            :schema "cong1_schema"}]
                [:dispatch! user-is-present-event]]
               (spy/read! spy)))))

    (testing "pending GIS user deletion"
      (let [state (apply-events [cong-created-event schema-is-present-event
                                 user-created-event user-is-present-event
                                 user-deleted-event])]
        (db-admin/process-pending-changes! state injections)
        (is (= [[:ensure-gis-user-absent! {:username "username123"
                                           :schema "cong1_schema"}]
                [:dispatch! user-is-absent-event]]
               (spy/read! spy)))))))
