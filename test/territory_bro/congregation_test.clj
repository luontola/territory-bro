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
            [territory-bro.fixtures :refer [db-fixture event-actor-fixture]]
            [territory-bro.projections :as projections]
            [territory-bro.testutil :as testutil])
  (:import (java.util UUID)))

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
              (is (= expected (apply-events events))))))))))

(deftest congregations-test
  (db/with-db [conn {:isolation :read-committed}] ; creating the schema happens in another transaction
    (jdbc/db-set-rollback-only! conn)

    (testing "create congregation"
      (let [id (congregation/create-congregation! conn "the name")
            congregation (get-in (projections/current-state conn) [::congregation/congregations id])]
        (is id)
        (is (= [{:event/type :congregation.event/congregation-created
                 :event/version 1
                 :event/system "test"
                 :congregation/id id
                 :congregation/name "the name"
                 :congregation/schema-name (:congregation/schema-name congregation)}]
               (->> (event-store/read-stream conn id)
                    (map #(dissoc % :event/stream-id :event/stream-revision :event/global-revision :event/time)))))
        (is (= id (:congregation/id congregation)))
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
        (is (= [cong-id] (keys (congregation/get-my-congregations state user-id)))))

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
