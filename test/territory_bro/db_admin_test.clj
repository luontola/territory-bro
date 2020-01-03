;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin-test
  (:require [clojure.test :refer :all]
            [territory-bro.commands :as commands]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.events :as events]
            [territory-bro.spy :as spy]
            [territory-bro.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro ValidationException NoPermitException)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def test-time (Instant/ofEpochSecond 42))
(def congregation-created {:event/type :congregation.event/congregation-created
                           :event/version 1
                           :congregation/id cong-id
                           :congregation/name "Cong1 Name"
                           :congregation/schema-name "cong1_schema"})
(def gis-user-created {:event/type :congregation.event/gis-user-created
                       :event/version 1
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "username123"
                       :gis-user/password "password123"})
(def gis-user-deleted {:event/type :congregation.event/gis-user-deleted
                       :event/version 1
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "username123"})
(def gis-schema-is-present {:event/type :db-admin.event/gis-schema-is-present
                            :event/version 1
                            :event/transient? true
                            :congregation/id cong-id
                            :congregation/schema-name "cong1_schema"})
(def gis-user-is-present {:event/type :db-admin.event/gis-user-is-present
                          :event/version 1
                          :event/transient? true
                          :congregation/id cong-id
                          :user/id user-id
                          :gis-user/username "username123"})
(def gis-user-is-absent {:event/type :db-admin.event/gis-user-is-absent
                         :event/version 1
                         :event/transient? true
                         :congregation/id cong-id
                         :user/id user-id
                         :gis-user/username "username123"})

(defn- apply-events [events]
  (testutil/apply-events db-admin/projection events))

(defn- generate-commands [events]
  (->> (db-admin/generate-commands (apply-events events)
                                   {:now (fn [] test-time)})
       (commands/validate-commands)))

(defn- handle-command [command state injections]
  (->> (db-admin/handle-command (commands/validate-command command)
                                state
                                injections)
       (events/validate-events)))

(deftest generate-commands-test
  (testing "congregation created"
    (let [events [congregation-created]]
      (is (= [{:command/type :db-admin.command/migrate-tenant-schema
               :command/time test-time
               :command/system "territory-bro.db-admin"
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
                       :command/system "territory-bro.db-admin"
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
                               :command/system "territory-bro.db-admin"
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
                               :command/system "territory-bro.db-admin"
                               :congregation/id cong-id
                               :congregation/schema-name "cong1_schema"
                               :user/id user-id
                               :gis-user/username "username123"}]
                             (generate-commands events)))

                      (testing "> GIS user is absent"
                        (let [events (conj events gis-user-is-absent)]
                          (is (empty? (generate-commands events))))))))))))))))

(deftest migrate-tenant-schema-test
  (let [spy (spy/spy)
        state (apply-events [congregation-created])
        injections {:migrate-tenant-schema! (spy/fn spy :migrate-tenant-schema!)
                    :check-permit (fn [_permit])
                    :check-congregation-exists (fn [_cong-id])}
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

    (testing "checks congregation exists"
      (let [injections (assoc injections
                              :check-congregation-exists (fn [id]
                                                           (is (= cong-id id))
                                                           (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException
                     (handle-command command state injections)))))

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
                    :check-permit (fn [_permit])
                    :check-congregation-exists (fn [_cong-id])
                    :check-user-exists (fn [_user-id])}
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

    (testing "checks congregation exists"
      (let [injections (assoc injections
                              :check-congregation-exists (fn [id]
                                                           (is (= cong-id id))
                                                           (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException (handle-command command state injections)))))

    (testing "checks user exists"
      (let [injections (assoc injections
                              :check-user-exists (fn [id]
                                                   (is (= user-id id))
                                                   (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException (handle-command command state injections)))))

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
                    :check-permit (fn [_permit])
                    :check-congregation-exists (fn [_cong-id])
                    :check-user-exists (fn [_user-id])}
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

    (testing "checks congregation exists"
      (let [injections (assoc injections
                              :check-congregation-exists (fn [id]
                                                           (is (= cong-id id))
                                                           (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException (handle-command command state injections)))))

    (testing "checks user exists"
      (let [injections (assoc injections
                              :check-user-exists (fn [id]
                                                   (is (= user-id id))
                                                   (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException (handle-command command state injections)))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:ensure-gis-user-absent cong-id user-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command command state injections)))))))
