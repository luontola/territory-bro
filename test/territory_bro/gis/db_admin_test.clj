;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.db-admin-test
  (:require [clojure.test :refer :all]
            [territory-bro.events :as events]
            [territory-bro.gis.db-admin :as db-admin]
            [territory-bro.test.spy :as spy]
            [territory-bro.test.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def test-time (Instant/ofEpochSecond 42))
(def congregation-created {:event/type :congregation.event/congregation-created
                           :congregation/id cong-id
                           :congregation/name "Cong1 Name"
                           :congregation/schema-name "cong1_schema"})
(def gis-user-created {:event/type :congregation.event/gis-user-created
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "username123"
                       :gis-user/password "password123"})
(def gis-user-deleted {:event/type :congregation.event/gis-user-deleted
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "username123"})
(def gis-schema-is-present {:event/type :db-admin.event/gis-schema-is-present
                            :event/transient? true
                            :congregation/id cong-id
                            :congregation/schema-name "cong1_schema"})
(def gis-user-is-present {:event/type :db-admin.event/gis-user-is-present
                          :event/transient? true
                          :congregation/id cong-id
                          :user/id user-id
                          :gis-user/username "username123"})
(def gis-user-is-absent {:event/type :db-admin.event/gis-user-is-absent
                         :event/transient? true
                         :congregation/id cong-id
                         :user/id user-id
                         :gis-user/username "username123"})

(defn- apply-events [events]
  (testutil/apply-events db-admin/projection events))

(defn- generate-commands [events]
  (->> (db-admin/generate-commands (apply-events events)
                                   {:now (fn [] test-time)})
       (testutil/validate-commands)))

(defn- handle-command [command state injections]
  (->> (db-admin/handle-command (testutil/validate-command command)
                                state
                                injections)
       (events/validate-events)))

(deftest generate-commands-test
  (testing "congregation created"
    (let [events [congregation-created]]
      (is (= [{:command/type :db-admin.command/migrate-tenant-schema
               :command/time test-time
               :command/system "territory-bro.gis.db-admin"
               :congregation/id cong-id
               :congregation/schema-name "cong1_schema"}]
             (generate-commands events)))

      (testing "> GIS schema is present"
        (let [events (conj events gis-schema-is-present)]
          (is (empty? (generate-commands events)))

          ;; TODO: there could be a different process manager for dealing with GIS users
          (testing "> GIS user created"
            (let [events (conj events gis-user-created)]
              (is (= [{:command/type :db-admin.command/ensure-gis-user-present
                       :command/time test-time
                       :command/system "territory-bro.gis.db-admin"
                       :congregation/id cong-id
                       :congregation/schema-name "cong1_schema"
                       :user/id user-id
                       :gis-user/password "password123"
                       :gis-user/username "username123"}]
                     (generate-commands events)))

              (testing "> GIS user is present"
                (let [events (conj events gis-user-is-present)]
                  (is (empty? (generate-commands events)))

                  (testing "> GIS user password changed"
                    (let [events (conj events (assoc gis-user-created :gis-user/password "new password"))]
                      (is (= [{:command/type :db-admin.command/ensure-gis-user-present
                               :command/time test-time
                               :command/system "territory-bro.gis.db-admin"
                               :congregation/id cong-id
                               :congregation/schema-name "cong1_schema"
                               :user/id user-id
                               :gis-user/password "new password"
                               :gis-user/username "username123"}]
                             (generate-commands events)))))

                  (testing "> GIS user deleted"
                    (let [events (conj events gis-user-deleted)]
                      (is (= [{:command/type :db-admin.command/ensure-gis-user-absent
                               :command/time test-time
                               :command/system "territory-bro.gis.db-admin"
                               :congregation/id cong-id
                               :congregation/schema-name "cong1_schema"
                               :user/id user-id
                               :gis-user/username "username123"}]
                             (generate-commands events)))

                      (testing "> GIS user is absent"
                        (let [events (conj events gis-user-is-absent)]
                          (is (empty? (generate-commands events))))))))))))))))

(defn- init-present-users [events present-users]
  (->> (db-admin/init-present-users (apply-events events)
                                    {:get-present-users (fn [] present-users)})
       (events/validate-events)))

(deftest init-present-users-test
  (testing "no users present in database"
    (is (empty? (init-present-users [] []))))

  (testing "some users present in database"
    (is (= [gis-user-is-present]
           (init-present-users [congregation-created
                                gis-user-created]
                               [{:username "username123"
                                 :schema "cong1_schema"}]))))

  (testing "username not found"
    (is (empty? (init-present-users [congregation-created
                                     gis-user-created]
                                    [{:username "foo"
                                      :schema "cong1_schema"}]))))

  (testing "schema not found"
    (is (empty? (init-present-users [congregation-created
                                     gis-user-created]
                                    [{:username "username123"
                                      :schema "foo"}])))))

(deftest migrate-tenant-schema-test
  (let [spy (spy/spy)
        state (apply-events [congregation-created])
        injections {:migrate-tenant-schema! (spy/fn spy :migrate-tenant-schema!)
                    :check-permit (fn [_permit])}
        command {:command/type :db-admin.command/migrate-tenant-schema
                 :command/time (Instant/now)
                 :command/system "test"
                 :congregation/id cong-id
                 :congregation/schema-name "cong1_schema"}]

    (testing "valid command"
      (is (= [gis-schema-is-present]
             (handle-command command state injections)))
      (is (= [[:migrate-tenant-schema! "cong1_schema"]]
             (spy/read! spy))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:migrate-tenant-schema cong-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command command state injections)))))))

(deftest ensure-gis-user-present-test
  (let [spy (spy/spy)
        state (apply-events [congregation-created gis-user-created])
        injections {:ensure-gis-user-present! (spy/fn spy :ensure-gis-user-present!)
                    :check-permit (fn [_permit])}
        command {:command/type :db-admin.command/ensure-gis-user-present
                 :command/time (Instant/now)
                 :command/system "test"
                 :user/id user-id
                 :gis-user/username "username123"
                 :gis-user/password "password123"
                 :congregation/id cong-id
                 :congregation/schema-name "cong1_schema"}]

    (testing "valid command"
      (is (= [gis-user-is-present]
             (handle-command command state injections)))
      (is (= [[:ensure-gis-user-present! {:username "username123"
                                          :password "password123"
                                          :schema "cong1_schema"}]]
             (spy/read! spy))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:ensure-gis-user-present cong-id user-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command command state injections)))))))

(deftest ensure-gis-user-absent-test
  (let [spy (spy/spy)
        state (apply-events [congregation-created gis-user-created])
        injections {:ensure-gis-user-absent! (spy/fn spy :ensure-gis-user-absent!)
                    :check-permit (fn [_permit])}
        command {:command/type :db-admin.command/ensure-gis-user-absent
                 :command/time (Instant/now)
                 :command/system "test"
                 :user/id user-id
                 :gis-user/username "username123"
                 :congregation/id cong-id
                 :congregation/schema-name "cong1_schema"}]

    (testing "valid command"
      (is (= [gis-user-is-absent]
             (handle-command command state injections)))
      (is (= [[:ensure-gis-user-absent! {:username "username123"
                                         :schema "cong1_schema"}]]
             (spy/read! spy))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:ensure-gis-user-absent cong-id user-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command command state injections)))))))
