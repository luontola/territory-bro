;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [medley.core :refer [deep-merge]]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.events :as events]
            [territory-bro.fixtures :refer [db-fixture event-actor-fixture]]
            [territory-bro.gis :as gis]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.projections :as projections]
            [territory-bro.region :as region]
            [territory-bro.territory :as territory]
            [territory-bro.testdata :as testdata]
            [territory-bro.testutil :as testutil]
            [territory-bro.user :as user])
  (:import (java.time Instant)
           (java.util UUID)
           (org.postgresql.util PSQLException)))

(use-fixtures :once (join-fixtures [db-fixture event-actor-fixture]))

(defn- apply-events [events]
  (testutil/apply-events gis-user/gis-users-view events))

(deftest gis-users-view-test
  (testing "congregation created"
    (let [cong-id (UUID. 0 1)
          user-id (UUID. 0 2)
          events [{:event/type :congregation.event/congregation-created
                   :event/version 1
                   :congregation/id cong-id
                   :congregation/name "Cong1 Name"
                   :congregation/schema-name "cong1_schema"}]
          expected {::gis-user/congregations
                    {cong-id {:congregation/id cong-id
                              :congregation/schema-name "cong1_schema"}}
                    ::gis-user/need-to-create #{}
                    ::gis-user/need-to-delete #{}}]
      (is (= expected (apply-events events)))

      (testing "> GIS access granted"
        (let [events (conj events {:event/type :congregation.event/permission-granted
                                   :event/version 1
                                   :congregation/id cong-id
                                   :user/id user-id
                                   :permission/id :gis-access})
              expected (deep-merge expected
                                   {::gis-user/congregations
                                    {cong-id {:congregation/users {user-id {:user/id user-id
                                                                            :user/has-gis-access? true}}}}
                                    ::gis-user/need-to-create #{{:congregation/id cong-id
                                                                 :user/id user-id}}})]
          (is (= expected (apply-events events)))

          (testing "> GIS user created"
            (let [events (conj events {:event/type :congregation.event/gis-user-created
                                       :event/version 1
                                       :congregation/id cong-id
                                       :user/id user-id
                                       :gis-user/username "username123"
                                       :gis-user/password "password123"})
                  expected (deep-merge expected
                                       {::gis-user/congregations
                                        {cong-id {:congregation/users {user-id {:user/id user-id
                                                                                :gis-user/desired-state :present
                                                                                :gis-user/username "username123"
                                                                                :gis-user/password "password123"}}}}
                                        ::gis-user/need-to-create #{}})]
              (is (= expected (apply-events events)))

              (testing "> GIS access revoked"
                (let [events (conj events {:event/type :congregation.event/permission-revoked
                                           :event/version 1
                                           :congregation/id cong-id
                                           :user/id user-id
                                           :permission/id :gis-access})
                      expected (deep-merge expected
                                           {::gis-user/congregations
                                            {cong-id {:congregation/users {user-id {:user/has-gis-access? false}}}}
                                            ::gis-user/need-to-delete #{{:congregation/id cong-id
                                                                         :user/id user-id}}})]
                  (is (= expected (apply-events events)))

                  (testing "> GIS user deleted"
                    (let [events (conj events {:event/type :congregation.event/gis-user-deleted
                                               :event/version 1
                                               :congregation/id cong-id
                                               :user/id user-id
                                               :gis-user/username "username123"})
                          expected (deep-merge expected
                                               {::gis-user/congregations
                                                {cong-id {:congregation/users {user-id {:gis-user/desired-state :absent
                                                                                        :gis-user/password nil}}}}
                                                ::gis-user/need-to-delete #{}})]
                      (is (= expected (apply-events events)))))))

              (testing "> unrelated permission revoked"
                (let [events (conj events {:event/type :congregation.event/permission-revoked
                                           :event/version 1
                                           :congregation/id cong-id
                                           :user/id user-id
                                           :permission/id :view-congregation})]
                  (is (= expected (apply-events events)) "should ignore the event")))))))

      (testing "> unrelated permission granted"
        (let [events (conj events {:event/type :congregation.event/permission-granted
                                   :event/version 1
                                   :congregation/id cong-id
                                   :user/id user-id
                                   :permission/id :view-congregation})]
          (is (= expected (apply-events events)) "should ignore the event"))))))

(defn- handle-command [command events injections]
  (let [events (events/validate-events events)
        new-events (gis-user/handle-command command events injections)]
    (events/validate-events new-events)))

(deftest commands-test
  (let [cong-id (UUID. 1 0)
        user-id (UUID. 2 0)
        injections {:generate-password (constantly "secret123")}
        create-command {:command/type :gis-user.command/create-gis-user
                        :command/time (Instant/ofEpochSecond 1)
                        :command/user user-id
                        :congregation/id cong-id
                        :user/id user-id}
        created-event {:event/type :congregation.event/gis-user-created
                       :event/version 1
                       :event/time (Instant/ofEpochSecond 1)
                       :event/user user-id
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "gis_user_0000000000000001_0000000000000002"
                       :gis-user/password "secret123"}
        delete-command {:command/type :gis-user.command/delete-gis-user
                        :command/time (Instant/ofEpochSecond 2)
                        :command/user user-id
                        :congregation/id cong-id
                        :user/id user-id}
        deleted-event {:event/type :congregation.event/gis-user-deleted
                       :event/version 1
                       :event/time (Instant/ofEpochSecond 2)
                       :event/user user-id
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "gis_user_0000000000000001_0000000000000002"}]

    (testing "create GIS user"
      (is (= [created-event]
             (handle-command create-command [] injections))))

    (testing "create is idempotent")

    (testing "delete GIS user") ; TODO

    (testing "delete is idempotent")

    (testing "enforces unique usernames"))) ; TODO: query database for existing role?

(deftest gis-users-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "cong1")
          cong-id2 (congregation/create-congregation! conn "cong2")
          user-id (user/save-user! conn "user1" {})
          user-id2 (user/save-user! conn "user2" {})]

      (gis-user/create-gis-user! conn (projections/current-state conn) cong-id user-id)
      (gis-user/create-gis-user! conn (projections/current-state conn) cong-id user-id2)
      (gis-user/create-gis-user! conn (projections/current-state conn) cong-id2 user-id)
      (gis-user/create-gis-user! conn (projections/current-state conn) cong-id2 user-id2)
      (testing "create & get GIS user"
        (let [user (gis-user/get-gis-user (projections/current-state conn) cong-id user-id)]
          (is (:gis-user/username user))
          (is (= 50 (count (:gis-user/password user))))))

      (testing "delete GIS user"
        (gis-user/delete-gis-user! conn (projections/current-state conn) cong-id user-id)
        (is (nil? (gis-user/get-gis-user (projections/current-state conn) cong-id user-id)))
        (is (gis-user/get-gis-user (projections/current-state conn) cong-id2 user-id2)
            "should not delete unrelated users")))))

(defn- gis-db-spec [username password]
  {:connection-uri (-> (:database-url config/env)
                       (str/replace #"\?.*" "")) ; strip username and password
   :user username
   :password password})

(defn- create-tenant-schema! [conn]
  (let [cong-id (congregation/create-congregation! conn "cong1")
        cong (congregation/get-unrestricted-congregation
              (projections/current-state conn) cong-id)]
    (:congregation/schema-name cong)))

(defn- login-as [username password]
  (= [{:user username}] (jdbc/query (gis-db-spec username password)
                                    ["select session_user as user"])))

(deftest ensure-gis-user-present-or-absent-test
  (let [schema (create-tenant-schema! db/database)
        username "gis_user_1"]

    (testing "create account"
      (gis-user/ensure-present! db/database {:username username
                                             :password "password1"
                                             :schema schema})
      (is (login-as username "password1")))

    (testing "update account / create is idempotent"
      (gis-user/ensure-present! db/database {:username username
                                             :password "password2"
                                             :schema schema})
      (is (login-as username "password2")))

    (testing "delete account"
      (gis-user/ensure-absent! db/database {:username username
                                            :schema schema})
      (is (thrown? PSQLException (login-as username "password2"))))

    (testing "delete is idempotent"
      (gis-user/ensure-absent! db/database {:username username
                                            :schema schema})
      (is (thrown? PSQLException (login-as username "password2"))))))

(defn- create-test-data! []
  (db/with-db [conn {}]
    (let [cong-id (congregation/create-congregation! conn "cong")
          cong (congregation/get-unrestricted-congregation (projections/current-state conn) cong-id)
          user-id (user/save-user! conn "user" {})
          _ (gis-user/create-gis-user! conn (projections/current-state conn) cong-id user-id)
          gis-user (gis-user/get-gis-user (projections/current-state conn) cong-id user-id)]
      {:cong-id cong-id
       :user-id user-id
       :schema (:congregation/schema-name cong)
       :username (:gis-user/username gis-user)
       :password (:gis-user/password gis-user)})))

(deftest gis-user-database-access-test
  (let [{:keys [cong-id user-id schema username password]} (create-test-data!)
        db-spec (gis-db-spec username password)]

    (testing "can login to the database"
      (is (= [{:test 1}] (jdbc/query db-spec ["select 1 as test"]))))

    (testing "can view the tenant schema and the tables in it"
      (jdbc/with-db-transaction [conn db-spec]
        (is (jdbc/query conn [(str "select * from " schema ".territory")]))
        (is (jdbc/query conn [(str "select * from " schema ".congregation_boundary")]))
        (is (jdbc/query conn [(str "select * from " schema ".subregion")]))
        (is (jdbc/query conn [(str "select * from " schema ".card_minimap_viewport")]))))

    (testing "can modify data in the tenant schema"
      (jdbc/with-db-transaction [conn db-spec]
        (db/use-tenant-schema conn schema)
        (is (territory/create-territory! conn {:territory/number "123"
                                               :territory/addresses "Street 1 A"
                                               :territory/subregion "Somewhere"
                                               :territory/meta {:foo "bar", :gazonk 42}
                                               :territory/location testdata/wkt-multi-polygon}))
        (is (region/create-congregation-boundary! conn testdata/wkt-multi-polygon))
        (is (region/create-subregion! conn "Somewhere" testdata/wkt-multi-polygon))
        (is (region/create-card-minimap-viewport! conn testdata/wkt-polygon))))

    (testing "user ID is logged in GIS change log"
      (db/with-db [conn {}]
        (is (= [{:op "INSERT",
                 :schema schema,
                 :table "territory",
                 :user username}
                {:op "INSERT",
                 :schema schema,
                 :table "congregation_boundary",
                 :user username}
                {:op "INSERT",
                 :schema schema,
                 :table "subregion",
                 :user username}
                {:op "INSERT",
                 :schema schema,
                 :table "card_minimap_viewport",
                 :user username}]
               (->> (gis/get-gis-changes conn)
                    (map #(dissoc % :id :time :old :new)))))))

    (testing "cannot view the master schema"
      (is (thrown-with-msg? PSQLException #"ERROR: permission denied for schema"
                            (jdbc/query db-spec [(str "select * from " (:database-schema config/env) ".congregation")]))))

    (testing "cannot create objects in the public schema"
      (is (thrown-with-msg? PSQLException #"ERROR: permission denied for schema public"
                            (jdbc/query db-spec ["create table public.foo (id serial primary key)"]))))

    (testing "cannot login to database after user is deleted"
      (db/with-db [conn {}]
        (gis-user/delete-gis-user! conn (projections/current-state conn) cong-id user-id))
      (is (thrown-with-msg? PSQLException #"FATAL: password authentication failed for user"
                            (jdbc/query db-spec ["select 1 as test"]))))))

(deftest generate-password-test
  (let [a (gis-user/generate-password 10)
        b (gis-user/generate-password 10)]
    (is (= 10 (count a) (count b)))
    (is (not= a b))))
