;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [medley.core :refer [deep-merge]]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events]
            [territory-bro.fixtures :refer [db-fixture event-actor-fixture]]
            [territory-bro.projections :as projections]
            [territory-bro.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)))

(use-fixtures :once (join-fixtures [db-fixture event-actor-fixture]))

(defn- apply-events [events]
  (testutil/apply-events congregation/congregations-view events))

(deftest congregations-view-test
  (testing "created"
    (let [cong-id (UUID. 0 1)
          events [{:event/type :congregation.event/congregation-created
                   :event/version 1
                   :congregation/id cong-id
                   :congregation/name "Cong1 Name"
                   :congregation/schema-name "cong1_schema"}]
          expected {::congregation/congregations
                    {cong-id {:congregation/id cong-id
                              :congregation/name "Cong1 Name"
                              :congregation/schema-name "cong1_schema"}}}]
      (is (= expected (apply-events events)))

      (testing "> permission granted"
        (let [user-id (UUID. 0 2)
              events (conj events {:event/type :congregation.event/permission-granted
                                   :event/version 1
                                   :congregation/id cong-id
                                   :user/id user-id
                                   :permission/id :view-congregation})
              expected (deep-merge expected
                                   {::congregation/congregations
                                    {cong-id {:congregation/user-permissions {user-id #{:view-congregation}}}}})]
          (is (= expected (apply-events events)))

          (testing "> permissing revoked"
            (let [events (conj events {:event/type :congregation.event/permission-revoked
                                       :event/version 1
                                       :congregation/id cong-id
                                       :user/id user-id
                                       :permission/id :view-congregation})
                  expected (deep-merge expected
                                       {::congregation/congregations
                                        {cong-id {:congregation/user-permissions {user-id #{}}}}})]
              (is (= expected (apply-events events)))))))

      (testing "> congregation renamed"
        (let [events (conj events {:event/type :congregation.event/congregation-renamed
                                   :event/version 1
                                   :congregation/id cong-id
                                   :congregation/name "New Name"})
              expected (deep-merge expected
                                   {::congregation/congregations
                                    {cong-id {:congregation/name "New Name"}}})]
          (is (= expected (apply-events events))))))))

(deftest congregations-test
  (db/with-db [conn {:isolation :read-committed}] ; creating the schema happens in another transaction
    (jdbc/db-set-rollback-only! conn)

    (testing "create congregation"
      (let [cong-id (congregation/create-congregation! conn "the name")
            congregation (congregation/get-unrestricted-congregation (projections/current-state conn) cong-id)]
        (is cong-id)
        (is (= [{:event/type :congregation.event/congregation-created
                 :event/version 1
                 :event/system "test"
                 :congregation/id cong-id
                 :congregation/name "the name"
                 :congregation/schema-name (:congregation/schema-name congregation)}]
               (->> (event-store/read-stream conn cong-id)
                    (map #(dissoc % :event/stream-id :event/stream-revision :event/global-revision :event/time)))))
        (is (= cong-id (:congregation/id congregation)))
        (is (= "the name" (:congregation/name congregation)))
        (is (contains? (set (db/get-schemas conn))
                       (:congregation/schema-name congregation))
            "should create congregation schema")))))

(deftest congregation-access-test
  (let [cong-id (UUID. 0 1)
        unrelated-cong-id (UUID. 0 2)
        user-id (UUID. 0 3)
        events [{:event/type :congregation.event/congregation-created
                 :event/version 1
                 :congregation/id cong-id
                 :congregation/name "Cong1 Name"
                 :congregation/schema-name "cong1_schema"}
                {:event/type :congregation.event/congregation-created
                 :event/version 1
                 :congregation/id unrelated-cong-id
                 :congregation/name "Cong2 Name"
                 :congregation/schema-name "cong2_schema"}]
        state (apply-events events)]

    (testing "cannot see congregations by default"
      (is (nil? (congregation/get-my-congregation state cong-id user-id)))
      (is (empty? (congregation/get-my-congregations state user-id))))

    (let [events (conj events {:event/type :congregation.event/permission-granted
                               :event/version 1
                               :congregation/id cong-id
                               :user/id user-id
                               :permission/id :view-congregation})
          state (apply-events events)]
      (testing "can see congregations after granting access"
        (is (= cong-id (:congregation/id (congregation/get-my-congregation state cong-id user-id))))
        (is (= [cong-id] (->> (congregation/get-my-congregations state user-id)
                              (map :congregation/id)))))

      (testing "list users"
        (is (= [user-id] (congregation/get-users state cong-id)))
        (is (empty? (congregation/get-users state unrelated-cong-id))
            "unrelated congregation"))

      (let [events (conj events {:event/type :congregation.event/permission-revoked
                                 :event/version 1
                                 :congregation/id cong-id
                                 :user/id user-id
                                 :permission/id :view-congregation})
            state (apply-events events)]
        (testing "cannot see congregations after revoking access"
          (is (nil? (congregation/get-my-congregation state cong-id user-id)))
          (is (empty? (congregation/get-my-congregations state user-id))))

        (testing "list users"
          (is (empty? (congregation/get-users state cong-id)))
          (is (empty? (congregation/get-users state unrelated-cong-id))
              "unrelated congregation"))))

    (testing "superadmin can access all congregations"))) ; TODO


(defn- handle-command [events command]
  (let [events (events/validate-events events)
        new-events (congregation/handle-command events command)]
    (events/validate-events new-events)))

(deftest rename-congregation-test
  (let [cong-id (UUID. 0 1)
        user-id (UUID. 0 2)
        created-event {:event/type :congregation.event/congregation-created
                       :event/version 1
                       :event/time (Instant/ofEpochSecond 1)
                       :event/user user-id
                       :congregation/id cong-id
                       :congregation/name "old name"
                       :congregation/schema-name ""}
        renamed-event {:event/type :congregation.event/congregation-renamed
                       :event/version 1
                       :event/time (Instant/ofEpochSecond 2)
                       :event/user user-id
                       :congregation/id cong-id
                       :congregation/name "new name"}]

    (testing "name changed"
      (is (= [renamed-event]
             (handle-command [created-event]
                             {:command/type :congregation.command/rename-congregation
                              :command/time (Instant/ofEpochSecond 2)
                              :command/user user-id
                              :congregation/id cong-id
                              :congregation/name "new name"}))))

    (testing "name not changed"
      (testing "from original"
        (is (= [] (handle-command [created-event]
                                  {:command/type :congregation.command/rename-congregation
                                   :command/time (Instant/ofEpochSecond 3)
                                   :command/user user-id
                                   :congregation/id cong-id
                                   :congregation/name "old name"}))))
      (testing "from previous rename"
        (is (= [] (handle-command [created-event renamed-event]
                                  {:command/type :congregation.command/rename-congregation
                                   :command/user user-id
                                   :command/time (Instant/ofEpochSecond 3)
                                   :congregation/id cong-id
                                   :congregation/name "new name"})))))))
