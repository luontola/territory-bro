;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [medley.core :refer [deep-merge]]
            [territory-bro.commands :as commands]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.events :as events]
            [territory-bro.fixtures :refer [db-fixture process-managers-fixture event-actor-fixture]]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.projections :as projections]
            [territory-bro.territory :as territory]
            [territory-bro.testdata :as testdata]
            [territory-bro.testutil :as testutil]
            [territory-bro.user :as user])
  (:import (java.time Instant Duration)
           (java.util UUID)
           (org.postgresql.util PSQLException)
           (territory_bro ValidationException NoPermitException)))

(use-fixtures :once (join-fixtures [db-fixture process-managers-fixture event-actor-fixture]))

(def cong-id (UUID. 1 0))
(def user-id (UUID. 2 0))

(def congregation-created {:event/type :congregation.event/congregation-created
                           :event/version 1
                           :congregation/id cong-id
                           :congregation/name "Cong1 Name"
                           :congregation/schema-name "cong1_schema"})
(def permission-granted {:event/type :congregation.event/permission-granted
                         :event/version 1
                         :congregation/id cong-id
                         :user/id user-id
                         :permission/id :gis-access})
(def gis-user-created {:event/type :congregation.event/gis-user-created
                       :event/version 1
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "gis_user_0000000000000001_0000000000000002"
                       :gis-user/password "secret123"})
(def gis-user-deleted {:event/type :congregation.event/gis-user-deleted
                       :event/version 1
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "gis_user_0000000000000001_0000000000000002"})

(defn- apply-events [events]
  (testutil/apply-events gis-user/gis-users-view events))

(defn- handle-command [command events injections]
  (->> (gis-user/handle-command (commands/validate-command command)
                                (events/validate-events events)
                                injections)
       (events/validate-events)))

(deftest gis-users-view-test
  ;; TODO: reuse the event examples also in this test
  (testing "congregation created"
    (let [events [congregation-created]
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

(deftest create-gis-user-test
  (let [injections {:check-permit (fn [_permit])
                    :generate-password (constantly "secret123")
                    :db-user-exists? (constantly false)}
        command {:command/type :gis-user.command/create-gis-user
                 :command/time (Instant/now)
                 :command/system "test"
                 :congregation/id cong-id
                 :user/id user-id}
        events [congregation-created permission-granted]]

    (testing "valid command"
      (is (= [gis-user-created]
             (handle-command command events injections))))

    (testing "enforces unique usernames"
      (let [injections (assoc injections :db-user-exists? (fn [schema]
                                                            (contains? #{"gis_user_0000000000000001_0000000000000002"}
                                                                       schema)))]
        (is (= [(assoc gis-user-created :gis-user/username "gis_user_0000000000000001_0000000000000002_1")]
               (handle-command command events injections))))
      (let [injections (assoc injections :db-user-exists? (fn [schema]
                                                            (contains? #{"gis_user_0000000000000001_0000000000000002"
                                                                         "gis_user_0000000000000001_0000000000000002_1"
                                                                         "gis_user_0000000000000001_0000000000000002_2"
                                                                         "gis_user_0000000000000001_0000000000000002_3"}
                                                                       schema)))]
        (is (= [(assoc gis-user-created :gis-user/username "gis_user_0000000000000001_0000000000000002_4")]
               (handle-command command events injections)))))

    (testing "is idempotent"
      (is (empty? (handle-command command
                                  (conj events gis-user-created)
                                  injections))
          "already created"))

    (testing "recreating"
      (is (= [gis-user-created]
             (handle-command command
                             (conj events gis-user-created gis-user-deleted)
                             injections))))

    (testing "congregation doesn't exist"
      (is (thrown-with-msg?
           ValidationException (testutil/re-equals "[[:no-such-congregation #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (handle-command (assoc command :congregation/id (UUID. 0 0x666))
                           events
                           injections))))

    (testing "user doesn't exist"
      (is (thrown-with-msg?
           ValidationException (testutil/re-equals "[[:no-such-user #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (handle-command (assoc command :user/id (UUID. 0 0x666))
                           events
                           injections))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:create-gis-user cong-id user-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command command events injections)))))))

(deftest delete-gis-user-test
  (let [injections {:check-permit (fn [_permit])}
        command {:command/type :gis-user.command/delete-gis-user
                 :command/time (Instant/now)
                 :command/system "test"
                 :congregation/id cong-id
                 :user/id user-id}
        events [congregation-created permission-granted gis-user-created]]

    (testing "valid command"
      (is (= [gis-user-deleted]
             (handle-command command
                             events
                             injections))))

    (testing "username comes from the gis-user-created event and is not re-generated"
      (is (= [(assoc gis-user-deleted :gis-user/username "foo")]
             (handle-command command
                             (conj events (assoc gis-user-created :gis-user/username "foo"))
                             injections))))

    (testing "is idempotent"
      (is (empty? (handle-command command (drop-last events) injections))
          "not created")
      (is (empty? (handle-command command
                                  (conj events gis-user-deleted)
                                  injections))
          "already deleted"))

    (testing "congregation doesn't exist"
      (is (thrown-with-msg?
           ValidationException (testutil/re-equals "[[:no-such-congregation #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (handle-command (assoc command :congregation/id (UUID. 0 0x666))
                           events
                           injections))))

    (testing "user doesn't exist"
      (is (thrown-with-msg?
           ValidationException (testutil/re-equals "[[:no-such-user #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (handle-command (assoc command :user/id (UUID. 0 0x666))
                           events
                           injections))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:delete-gis-user cong-id user-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command command events injections)))))))

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
      (db/with-db [conn {}]
        (is (false? (gis-user/db-user-exists? conn username)))
        (gis-user/ensure-present! conn {:username username
                                        :password "password1"
                                        :schema schema})
        (is (true? (gis-user/db-user-exists? conn username))))
      (is (login-as username "password1")))

    (testing "update account / create is idempotent"
      (db/with-db [conn {}]
        (gis-user/ensure-present! conn {:username username
                                        :password "password2"
                                        :schema schema}))
      (is (login-as username "password2")))

    (testing "delete account"
      (db/with-db [conn {}]
        (gis-user/ensure-absent! conn {:username username
                                       :schema schema}))
      (is (thrown? PSQLException (login-as username "password2"))))

    (testing "delete is idempotent"
      (db/with-db [conn {}]
        (gis-user/ensure-absent! conn {:username username
                                       :schema schema}))
      (is (thrown? PSQLException (login-as username "password2"))))))

(defn- create-test-data-impl! []
  (try
    (db/with-db [conn {}]
      (let [cong-id (congregation/create-congregation! conn "cong")
            user-id (user/save-user! conn "user" {})]
        (congregation/grant! conn cong-id user-id :gis-access)
        {:cong-id cong-id
         :user-id user-id}))
    (finally
      ;; wait for process managers to create the GIS user
      (projections/refresh-async!)
      (projections/await-refreshed (Duration/ofSeconds 1)))))

(defn- create-test-data! []
  (let [{:keys [cong-id user-id]} (create-test-data-impl!)
        state (projections/cached-state)
        cong (congregation/get-unrestricted-congregation state cong-id)
        gis-user (gis-user/get-gis-user state cong-id user-id)]
    {:cong-id cong-id
     :user-id user-id
     :schema (:congregation/schema-name cong)
     :username (:gis-user/username gis-user)
     :password (:gis-user/password gis-user)}))

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
        (is (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon))
        (is (gis-db/create-subregion! conn "Somewhere" testdata/wkt-multi-polygon))
        (is (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon))))

    (testing "user ID is logged in GIS change log"
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
             (->> (gis-db/get-changes db/database)
                  (map #(dissoc % :id :time :old :new))))))

    (testing "cannot view the master schema"
      (is (thrown-with-msg? PSQLException #"ERROR: permission denied for schema"
                            (jdbc/query db-spec [(str "select * from " (:database-schema config/env) ".congregation")]))))

    (testing "cannot create objects in the public schema"
      (is (thrown-with-msg? PSQLException #"ERROR: permission denied for schema public"
                            (jdbc/query db-spec ["create table public.foo (id serial primary key)"]))))

    (testing "cannot login to database after user is deleted"
      (db/with-db [conn {}]
        (congregation/revoke! conn cong-id user-id :gis-access))
      (projections/refresh-async!)
      (projections/await-refreshed (Duration/ofSeconds 1)) ; wait for process managers to delete the GIS user

      (is (thrown-with-msg? PSQLException #"FATAL: password authentication failed for user"
                            (jdbc/query db-spec ["select 1 as test"]))))))

(deftest generate-password-test
  (let [a (gis-user/generate-password 10)
        b (gis-user/generate-password 10)]
    (is (= 10 (count a) (count b)))
    (is (not= a b))))
