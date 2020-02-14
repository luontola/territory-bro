;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.gis-db-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.testdata :as testdata])
  (:import (org.postgresql.util PSQLException)))

(def test-schema "test_gis_schema")
(def test-username "test_gis_user")

(defn- test-schema-setup [f]
  (let [schema (db/tenant-schema test-schema (:database-schema config/env))]
    (.migrate schema)
    (f)
    (.clean schema)))

(use-fixtures :each (join-fixtures [db-fixture test-schema-setup]))

(deftest regions-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)
    (db/use-tenant-schema conn test-schema)

    (testing "create & list congregation boundaries"
      (let [id (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon)]
        (is (= [{:region/id id
                 :region/location testdata/wkt-multi-polygon}]
               (gis-db/get-congregation-boundaries conn)))))

    (testing "create & list subregions"
      (let [id (gis-db/create-subregion! conn "the name" testdata/wkt-multi-polygon)]
        (is (= [{:region/id id
                 :region/name "the name"
                 :region/location testdata/wkt-multi-polygon}]
               (gis-db/get-subregions conn)))))

    (testing "create & list card minimap viewports"
      (let [id (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon)]
        (is (= [{:region/id id
                 :region/location testdata/wkt-polygon}]
               (gis-db/get-card-minimap-viewports conn)))))))

(deftest territories-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)
    (db/use-tenant-schema conn test-schema)

    (let [territory-id (gis-db/create-territory! conn {:territory/number "123"
                                                       :territory/addresses "Street 1 A"
                                                       :territory/subregion "Somewhere"
                                                       :territory/meta {:foo "bar", :gazonk 42}
                                                       :territory/location testdata/wkt-multi-polygon})]

      (testing "create new territory"
        (is territory-id))

      (testing "get territory by ID"
        (is (= {:territory/id territory-id
                :territory/number "123"
                :territory/addresses "Street 1 A"
                :territory/subregion "Somewhere"
                :territory/meta {:foo "bar", :gazonk 42}
                :territory/location testdata/wkt-multi-polygon}
               (gis-db/get-territory-by-id conn territory-id))))

      (testing "get territories by IDs"
        (is (= [territory-id]
               (->> (gis-db/get-territories conn {:ids [territory-id]})
                    (map :territory/id))))
        (is (= []
               (->> (gis-db/get-territories conn {:ids []})
                    (map :territory/id))))
        (is (= []
               (->> (gis-db/get-territories conn {:ids nil})
                    (map :territory/id)))))

      (testing "list territories"
        (is (= ["123"]
               (->> (gis-db/get-territories conn)
                    (map :territory/number)
                    (sort))))))))

(deftest gis-change-log-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)
    (db/use-tenant-schema conn test-schema)

    (testing "before making changes"
      (is (= [] (gis-db/get-changes conn))))

    (testing "territory table change log,"
      (let [territory-id (gis-db/create-territory! conn {:territory/number "123"
                                                         :territory/addresses "Street 1 A"
                                                         :territory/subregion "Somewhere"
                                                         :territory/meta {:foo "bar", :gazonk 42}
                                                         :territory/location testdata/wkt-multi-polygon})]
        (testing "insert"
          (let [changes (gis-db/get-changes conn)]
            (is (= 1 (count changes)))
            (is (= {:table "territory"
                    :op :INSERT
                    :old nil
                    :new {:id territory-id
                          :number "123"
                          :addresses "Street 1 A"
                          :subregion "Somewhere"
                          :meta {:foo "bar", :gazonk 42}
                          :location testdata/wkt-multi-polygon}}
                   (-> (last changes)
                       (dissoc :id :schema :user :time))))))

        (testing "update"
          (jdbc/execute! conn ["UPDATE territory SET addresses = 'Another Street 2' WHERE id = ?" territory-id])
          (let [changes (gis-db/get-changes conn)]
            (is (= 2 (count changes)))
            (is (= {:table "territory"
                    :op :UPDATE
                    :old {:id territory-id
                          :number "123"
                          :addresses "Street 1 A"
                          :subregion "Somewhere"
                          :meta {:foo "bar", :gazonk 42}
                          :location testdata/wkt-multi-polygon}
                    :new {:id territory-id
                          :number "123"
                          :addresses "Another Street 2"
                          :subregion "Somewhere"
                          :meta {:foo "bar", :gazonk 42}
                          :location testdata/wkt-multi-polygon}}
                   (-> (last changes)
                       (dissoc :id :schema :user :time))))))

        (testing "delete"
          (jdbc/execute! conn ["DELETE FROM territory WHERE id = ?" territory-id])
          (let [changes (gis-db/get-changes conn)]
            (is (= 3 (count changes)))
            (is (= {:table "territory"
                    :op :DELETE
                    :old {:id territory-id
                          :number "123"
                          :addresses "Another Street 2"
                          :subregion "Somewhere"
                          :meta {:foo "bar", :gazonk 42}
                          :location testdata/wkt-multi-polygon}
                    :new nil}
                   (-> (last changes)
                       (dissoc :id :schema :user :time))))))))

    (testing "congregation_boundary table change log"
      (let [region-id (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon)
            changes (gis-db/get-changes conn)]
        (is (= 4 (count changes)))
        (is (= {:table "congregation_boundary"
                :op :INSERT
                :old nil
                :new {:id region-id
                      :location testdata/wkt-multi-polygon}}
               (-> (last changes)
                   (dissoc :id :schema :user :time))))))

    (testing "subregion table change log"
      (let [region-id (gis-db/create-subregion! conn "Somewhere" testdata/wkt-multi-polygon)
            changes (gis-db/get-changes conn)]
        (is (= 5 (count changes)))
        (is (= {:table "subregion"
                :op :INSERT
                :old nil
                :new {:id region-id
                      :name "Somewhere"
                      :location testdata/wkt-multi-polygon}}
               (-> (last changes)
                   (dissoc :id :schema :user :time))))))

    (testing "card_minimap_viewport table change log"
      (let [region-id (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon)
            changes (gis-db/get-changes conn)]
        (is (= 6 (count changes)))
        (is (= {:table "card_minimap_viewport"
                :op :INSERT
                :old nil
                :new {:id region-id
                      :location testdata/wkt-polygon}}
               (-> (last changes)
                   (dissoc :id :schema :user :time))))))

    (testing "get changes since X"
      (let [all-changes (gis-db/get-changes conn)]
        (is (= all-changes
               (gis-db/get-changes conn {:since 0}))
            "since beginning")
        (is (= (drop 1 all-changes)
               (gis-db/get-changes conn {:since 1}))
            "since first change")
        (is (= (take-last 1 all-changes)
               (gis-db/get-changes conn {:since (:id (first (take-last 2 all-changes)))}))
            "since second last change")
        (is (= []
               (gis-db/get-changes conn {:since (:id (last all-changes))}))
            "since last change")
        ;; should probably be an error, but an empty result is safer than returning all events
        (is (= []
               (gis-db/get-changes conn {:since nil}))
            "since nil")))

    (testing "marking changes processed"
      (is (= [] (map :id (gis-db/get-changes conn {:processed? true})))
          "processed, before")
      (is (= [1 2 3 4 5 6] (map :id (gis-db/get-changes conn {:processed? false})))
          "unprocessed, before")

      (gis-db/mark-changes-processed! conn [1 2 4])

      (is (= [1 2 4] (map :id (gis-db/get-changes conn {:processed? true})))
          "processed, after")
      (is (= [3 5 6] (map :id (gis-db/get-changes conn {:processed? false})))
          "unprocessed, after"))))

(defn- gis-db-spec [username password]
  {:connection-uri (-> (:database-url config/env)
                       (str/replace #"\?.*" "")) ; strip username and password
   :user username
   :password password})

(defn- login-as [username password]
  (= [{:user username}] (jdbc/query (gis-db-spec username password)
                                    ["select session_user as user"])))

(deftest ensure-gis-user-present-or-absent-test
  (testing "create account"
    (db/with-db [conn {}]
      (is (false? (gis-db/user-exists? conn test-username)))
      (gis-db/ensure-user-present! conn {:username test-username
                                         :password "password1"
                                         :schema test-schema})
      (is (true? (gis-db/user-exists? conn test-username))))
    (is (login-as test-username "password1")))

  (testing "update account / create is idempotent"
    (db/with-db [conn {}]
      (gis-db/ensure-user-present! conn {:username test-username
                                         :password "password2"
                                         :schema test-schema}))
    (is (login-as test-username "password2")))

  (testing "delete account"
    (db/with-db [conn {}]
      (gis-db/ensure-user-absent! conn {:username test-username
                                        :schema test-schema}))
    (is (thrown? PSQLException (login-as test-username "password2"))))

  (testing "delete is idempotent"
    (db/with-db [conn {}]
      (gis-db/ensure-user-absent! conn {:username test-username
                                        :schema test-schema}))
    (is (thrown? PSQLException (login-as test-username "password2")))))

(deftest gis-user-database-access-test
  (let [password "password123"
        db-spec (gis-db-spec test-username password)]
    (db/with-db [conn {}]
      (gis-db/ensure-user-present! conn {:username test-username
                                         :password password
                                         :schema test-schema}))

    (testing "can login to the database"
      (is (= [{:test 1}] (jdbc/query db-spec ["select 1 as test"]))))

    (testing "can view the tenant schema and the tables in it"
      (jdbc/with-db-transaction [conn db-spec]
        (is (jdbc/query conn [(str "select * from " test-schema ".territory")]))
        (is (jdbc/query conn [(str "select * from " test-schema ".congregation_boundary")]))
        (is (jdbc/query conn [(str "select * from " test-schema ".subregion")]))
        (is (jdbc/query conn [(str "select * from " test-schema ".card_minimap_viewport")]))))

    (testing "can modify data in the tenant schema"
      (jdbc/with-db-transaction [conn db-spec]
        (db/use-tenant-schema conn test-schema)
        (is (gis-db/create-territory! conn {:territory/number "123"
                                            :territory/addresses "Street 1 A"
                                            :territory/subregion "Somewhere"
                                            :territory/meta {:foo "bar", :gazonk 42}
                                            :territory/location testdata/wkt-multi-polygon}))
        (is (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon))
        (is (gis-db/create-subregion! conn "Somewhere" testdata/wkt-multi-polygon))
        (is (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon))))

    (testing "user ID is logged in GIS change log"
      (is (= [{:op :INSERT
               :schema test-schema
               :table "territory"
               :user test-username}
              {:op :INSERT
               :schema test-schema
               :table "congregation_boundary"
               :user test-username}
              {:op :INSERT
               :schema test-schema
               :table "subregion"
               :user test-username}
              {:op :INSERT
               :schema test-schema
               :table "card_minimap_viewport"
               :user test-username}]
             (->> (db/with-db [conn {}]
                    (gis-db/get-changes conn))
                  (map #(dissoc % :id :time :old :new))))))

    (testing "cannot view the master schema"
      (is (thrown-with-msg? PSQLException #"ERROR: permission denied for schema"
                            (jdbc/query db-spec [(str "select * from " (:database-schema config/env) ".congregation")]))))

    (testing "cannot create objects in the public schema"
      (is (thrown-with-msg? PSQLException #"ERROR: permission denied for schema public"
                            (jdbc/query db-spec ["create table public.foo (id serial primary key)"]))))

    (testing "cannot login to database after user is deleted"
      (db/with-db [conn {}]
        (gis-db/ensure-user-absent! conn {:username test-username
                                          :schema test-schema}))
      (is (thrown-with-msg? PSQLException #"FATAL: password authentication failed for user"
                            (jdbc/query db-spec ["select 1 as test"]))))))
