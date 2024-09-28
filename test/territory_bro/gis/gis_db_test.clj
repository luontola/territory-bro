;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.gis.gis-db-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [next.jdbc :as jdbc]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.event-store :as event-store]
            [territory-bro.test.fixtures :refer [db-fixture]]
            [territory-bro.test.testutil :refer [re-equals thrown-with-msg? thrown?]])
  (:import (java.sql Connection)
           (java.util UUID)
           (org.apache.http.client.utils URIBuilder)
           (org.postgresql.util PSQLException)))

(def test-schema "test_gis_schema")
(def test-schema2 "test_gis_schema2")
(def test-username "test_gis_user")
(def test-username2 "test_gis_user2")

(defn- with-tenant-schema [schema-name f]
  (binding [db/*clean-disabled* false]
    (let [schema (db/tenant-schema schema-name (:database-schema config/env))]
      (try
        (.migrate schema)
        (f)
        (finally
          (db/with-db [conn {}]
            (db/execute-one! conn ["DELETE FROM gis_change_log"]))
          (.clean schema))))))

(defn test-schema-fixture [f]
  (with-tenant-schema test-schema f))

(use-fixtures :each (join-fixtures [db-fixture test-schema-fixture]))

(def example-territory
  {:territory/number "123"
   :territory/addresses "Street 1 A"
   :territory/region "Somewhere"
   :territory/meta {:foo "bar", :gazonk 42}
   :territory/location testdata/wkt-multi-polygon})

(deftest regions-test
  (db/with-db [conn {:rollback-only true}]
    (db/use-tenant-schema conn test-schema)

    (testing "create & list congregation boundaries"
      (let [id (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon)]
        (is (= [{:gis-feature/id id
                 :gis-feature/location testdata/wkt-multi-polygon}]
               (gis-db/get-congregation-boundaries conn)))))

    (testing "create & list regions"
      (let [id (gis-db/create-region! conn "the name" testdata/wkt-multi-polygon)]
        (is (= [{:gis-feature/id id
                 :gis-feature/name "the name"
                 :gis-feature/location testdata/wkt-multi-polygon}]
               (gis-db/get-regions conn)))))

    (testing "create & list card minimap viewports"
      (let [id (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon)]
        (is (= [{:gis-feature/id id
                 :gis-feature/location testdata/wkt-polygon}]
               (gis-db/get-card-minimap-viewports conn)))))))

(deftest territories-test
  (db/with-db [conn {:rollback-only true}]
    (db/use-tenant-schema conn test-schema)

    (let [territory-id (gis-db/create-territory! conn example-territory)]

      (testing "create new territory"
        (is territory-id))

      (testing "get territory by ID"
        (is (= {:gis-feature/id territory-id
                :gis-feature/number "123"
                :gis-feature/addresses "Street 1 A"
                :gis-feature/subregion "Somewhere"
                :gis-feature/meta {:foo "bar", :gazonk 42}
                :gis-feature/location testdata/wkt-multi-polygon}
               (gis-db/get-territory-by-id conn territory-id))))

      (testing "get territories by IDs"
        (is (= [territory-id]
               (->> (gis-db/get-territories conn {:ids [territory-id]})
                    (map :gis-feature/id))))
        (is (= []
               (->> (gis-db/get-territories conn {:ids []})
                    (map :gis-feature/id))))
        (is (= []
               (->> (gis-db/get-territories conn {:ids nil})
                    (map :gis-feature/id)))))

      (testing "list territories"
        (is (= ["123"]
               (->> (gis-db/get-territories conn)
                    (map :gis-feature/number)
                    (sort))))))))

(defn- init-stream! [conn id gis-schema, gis-table]
  (db/execute-one! conn ["INSERT INTO stream (stream_id, gis_schema, gis_table) VALUES (?, ?, ?)"
                         id gis-schema gis-table])
  id)

(deftest validate-gis-change-test
  (db/with-db [conn {:rollback-only true}]
    (db/use-tenant-schema conn test-schema)

    (testing "populates the stream table on insert"
      (let [id (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon)]
        (is (= {:stream_id id
                :entity_type nil
                :congregation nil
                :gis_schema test-schema
                :gis_table "congregation_boundary"}
               (event-store/stream-info conn id))
            "congregation boundary"))
      (let [id (gis-db/create-region! conn "the name" testdata/wkt-multi-polygon)]
        (is (= {:stream_id id
                :entity_type nil
                :congregation nil
                :gis_schema test-schema
                :gis_table "subregion"}
               (event-store/stream-info conn id))
            "region"))
      (let [id (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon)]
        (is (= {:stream_id id
                :entity_type nil
                :congregation nil
                :gis_schema test-schema
                :gis_table "card_minimap_viewport"}
               (event-store/stream-info conn id))
            "card minimap viewport"))
      (let [id (gis-db/create-territory! conn example-territory)]
        (is (= {:stream_id id
                :entity_type nil
                :congregation nil
                :gis_schema test-schema
                :gis_table "territory"}
               (event-store/stream-info conn id))
            "territory")))

    (testing "populates the stream table on ID change"
      (let [old-id (gis-db/create-region! conn "test" testdata/wkt-multi-polygon)
            new-id (UUID/randomUUID)]
        (db/execute-one! conn ["UPDATE subregion SET id = ? WHERE id = ?"
                               new-id old-id])
        (is (= {:stream_id old-id
                :entity_type nil
                :congregation nil
                :gis_schema test-schema
                :gis_table "subregion"}
               (event-store/stream-info conn old-id))
            "old stream")
        (is (= {:stream_id new-id
                :entity_type nil
                :congregation nil
                :gis_schema test-schema
                :gis_table "subregion"}
               (event-store/stream-info conn new-id))
            "new stream")))

    (testing "stream ID conflict detection:"
      (testing "stream ID is used by current schema and table -> keeps ID"
        (let [old-id (init-stream! conn (UUID/randomUUID) test-schema "subregion")
              new-id (gis-db/create-region-with-id! conn old-id "reused" testdata/wkt-multi-polygon)]
          (is (= old-id new-id))
          (is (= [{:name "reused"}]
                 (db/execute! conn ["SELECT name FROM subregion WHERE id = ?"
                                    new-id])))))

      (testing "stream ID is used by another schema -> replaces ID"
        (let [old-id (init-stream! conn (UUID/randomUUID) "another_schema" "subregion")
              new-id (gis-db/create-region-with-id! conn old-id "reused" testdata/wkt-multi-polygon)]
          (is (uuid? new-id))
          (is (not= old-id new-id))
          (is (= [{:name "reused"}]
                 (db/execute! conn ["SELECT name FROM subregion WHERE id = ?"
                                    new-id])))))

      (testing "stream ID is used by another table -> replaces ID"
        (let [old-id (init-stream! conn (UUID/randomUUID) test-schema "territory")
              new-id (gis-db/create-region-with-id! conn old-id "reused" testdata/wkt-multi-polygon)]
          (is (uuid? new-id))
          (is (not= old-id new-id))
          (is (= [{:name "reused"}]
                 (db/execute! conn ["SELECT name FROM subregion WHERE id = ?"
                                    new-id])))))

      (testing "stream ID is used by non-GIS entity -> replaces ID"
        (let [old-id (init-stream! conn (UUID/randomUUID) nil nil)
              new-id (gis-db/create-region-with-id! conn old-id "reused" testdata/wkt-multi-polygon)]
          (is (uuid? new-id))
          (is (not= old-id new-id))
          (is (= [{:name "reused"}]
                 (db/execute! conn ["SELECT name FROM subregion WHERE id = ?"
                                    new-id]))))))))

(deftest gis-change-log-test
  (db/with-db [conn {:rollback-only true}]
    (db/use-tenant-schema conn test-schema)

    (testing "before making changes"
      (is (= [] (gis-db/get-changes conn)))
      (is (nil? (gis-db/next-unprocessed-change conn))))

    (testing "territory table change log,"
      (let [territory-id (gis-db/create-territory! conn example-territory)]
        (testing "insert"
          (let [changes (gis-db/get-changes conn)]
            (is (= 1 (count changes)))
            (is (= {:gis-change/table "territory"
                    :gis-change/op :INSERT
                    :gis-change/old nil
                    :gis-change/new {:id territory-id
                                     :number "123"
                                     :addresses "Street 1 A"
                                     :subregion "Somewhere"
                                     :meta {:foo "bar", :gazonk 42}
                                     :location testdata/wkt-multi-polygon}
                    :gis-change/processed? false}
                   (-> (last changes)
                       (dissoc :gis-change/id :gis-change/schema :gis-change/user :gis-change/time))))))

        (testing "update"
          (db/execute-one! conn ["UPDATE territory SET addresses = 'Another Street 2' WHERE id = ?" territory-id])
          (let [changes (gis-db/get-changes conn)]
            (is (= 2 (count changes)))
            (is (= {:gis-change/table "territory"
                    :gis-change/op :UPDATE
                    :gis-change/old {:id territory-id
                                     :number "123"
                                     :addresses "Street 1 A"
                                     :subregion "Somewhere"
                                     :meta {:foo "bar", :gazonk 42}
                                     :location testdata/wkt-multi-polygon}
                    :gis-change/new {:id territory-id
                                     :number "123"
                                     :addresses "Another Street 2"
                                     :subregion "Somewhere"
                                     :meta {:foo "bar", :gazonk 42}
                                     :location testdata/wkt-multi-polygon}
                    :gis-change/processed? false}
                   (-> (last changes)
                       (dissoc :gis-change/id :gis-change/schema :gis-change/user :gis-change/time))))))

        (testing "delete"
          (db/execute-one! conn ["DELETE FROM territory WHERE id = ?" territory-id])
          (let [changes (gis-db/get-changes conn)]
            (is (= 3 (count changes)))
            (is (= {:gis-change/table "territory"
                    :gis-change/op :DELETE
                    :gis-change/old {:id territory-id
                                     :number "123"
                                     :addresses "Another Street 2"
                                     :subregion "Somewhere"
                                     :meta {:foo "bar", :gazonk 42}
                                     :location testdata/wkt-multi-polygon}
                    :gis-change/new nil
                    :gis-change/processed? false}
                   (-> (last changes)
                       (dissoc :gis-change/id :gis-change/schema :gis-change/user :gis-change/time))))))))

    (testing "congregation_boundary table change log"
      (let [region-id (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon)
            changes (gis-db/get-changes conn)]
        (is (= 4 (count changes)))
        (is (= {:gis-change/table "congregation_boundary"
                :gis-change/op :INSERT
                :gis-change/old nil
                :gis-change/new {:id region-id
                                 :location testdata/wkt-multi-polygon}
                :gis-change/processed? false}
               (-> (last changes)
                   (dissoc :gis-change/id :gis-change/schema :gis-change/user :gis-change/time))))))

    (testing "region table change log"
      (let [region-id (gis-db/create-region! conn "Somewhere" testdata/wkt-multi-polygon)
            changes (gis-db/get-changes conn)]
        (is (= 5 (count changes)))
        (is (= {:gis-change/table "subregion"
                :gis-change/op :INSERT
                :gis-change/old nil
                :gis-change/new {:id region-id
                                 :name "Somewhere"
                                 :location testdata/wkt-multi-polygon}
                :gis-change/processed? false}
               (-> (last changes)
                   (dissoc :gis-change/id :gis-change/schema :gis-change/user :gis-change/time))))))

    (testing "card_minimap_viewport table change log"
      (let [region-id (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon)
            changes (gis-db/get-changes conn)]
        (is (= 6 (count changes)))
        (is (= {:gis-change/table "card_minimap_viewport"
                :gis-change/op :INSERT
                :gis-change/old nil
                :gis-change/new {:id region-id
                                 :location testdata/wkt-polygon}
                :gis-change/processed? false}
               (-> (last changes)
                   (dissoc :gis-change/id :gis-change/schema :gis-change/user :gis-change/time))))))

    (testing "get changes since X"
      (let [all-changes (gis-db/get-changes conn)]
        (is (= all-changes
               (gis-db/get-changes conn {:since 0}))
            "since beginning")
        (is (= (drop 1 all-changes)
               (gis-db/get-changes conn {:since 1}))
            "since first change")
        (is (= (take-last 1 all-changes)
               (gis-db/get-changes conn {:since (:gis-change/id (first (take-last 2 all-changes)))}))
            "since second last change")
        (is (= []
               (gis-db/get-changes conn {:since (:gis-change/id (last all-changes))}))
            "since last change")
        ;; should probably be an error, but an empty result is safer than returning all events
        (is (= []
               (gis-db/get-changes conn {:since nil}))
            "since nil")))

    (testing "get changes, limit"
      (let [all-changes (gis-db/get-changes conn)]
        (is (= []
               (gis-db/get-changes conn {:limit 0}))
            "limit 0")
        (is (= (take 1 all-changes)
               (gis-db/get-changes conn {:limit 1}))
            "limit 1")
        (is (= (take 2 all-changes)
               (gis-db/get-changes conn {:limit 2}))
            "limit 2")
        ;; in PostgreSQL, LIMIT NULL (or LIMIT ALL) is the same as omitting the LIMIT
        (is (= all-changes
               (gis-db/get-changes conn {:limit nil}))
            "limit nil")))

    (testing "marking changes processed"
      (is (= [] (map :gis-change/id (gis-db/get-changes conn {:processed? true})))
          "processed, before")
      (is (= [1 2 3 4 5 6] (map :gis-change/id (gis-db/get-changes conn {:processed? false})))
          "unprocessed, before")
      (is (= 1 (:gis-change/id (gis-db/next-unprocessed-change conn)))
          "next unprocessed, before")

      (gis-db/mark-changes-processed! conn [1 2 4])

      (is (= [1 2 4] (map :gis-change/id (gis-db/get-changes conn {:processed? true})))
          "processed, after")
      (is (= [3 5 6] (map :gis-change/id (gis-db/get-changes conn {:processed? false})))
          "unprocessed, after")
      (is (= 3 (:gis-change/id (gis-db/next-unprocessed-change conn)))
          "next unprocessed, after"))))

(defn- gis-db-spec [username password]
  (let [url (str/replace-first (:database-url config/env) "jdbc:" "")
        url (str "jdbc:" (-> (URIBuilder. url)
                             (.setParameter "user" username)
                             (.setParameter "password" password)))]
    {:connection-uri url}))

(defn- login-as [username password]
  (with-open [conn (jdbc/get-connection (gis-db-spec username password))]
    (= {:user username} (db/execute-one! conn ["select session_user as user"]))))

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
        user-db-spec (gis-db-spec test-username password)]
    (db/with-db [admin-conn {}]
      (gis-db/ensure-user-present! admin-conn {:username test-username
                                               :password password
                                               :schema test-schema}))

    (testing "can login to the database"
      (with-open [user-conn (jdbc/get-connection user-db-spec)]
        (is (= {:test 1} (db/execute-one! user-conn ["select 1 as test"])))))

    (with-open [user-conn (jdbc/get-connection user-db-spec)]
      (testing "can view the tenant schema and the tables in it"
        (is (db/execute! user-conn [(str "select * from " test-schema ".territory")]))
        (is (db/execute! user-conn [(str "select * from " test-schema ".congregation_boundary")]))
        (is (db/execute! user-conn [(str "select * from " test-schema ".subregion")]))
        (is (db/execute! user-conn [(str "select * from " test-schema ".card_minimap_viewport")])))

      (testing "can modify data in the tenant schema"
        (db/use-tenant-schema user-conn test-schema)
        (is (gis-db/create-territory! user-conn example-territory))
        (is (gis-db/create-congregation-boundary! user-conn testdata/wkt-multi-polygon))
        (is (gis-db/create-region! user-conn "Somewhere" testdata/wkt-multi-polygon))
        (is (gis-db/create-card-minimap-viewport! user-conn testdata/wkt-polygon)))

      (testing "user ID is logged in GIS change log"
        (is (= [{:gis-change/op :INSERT
                 :gis-change/schema test-schema
                 :gis-change/table "territory"
                 :gis-change/user test-username}
                {:gis-change/op :INSERT
                 :gis-change/schema test-schema
                 :gis-change/table "congregation_boundary"
                 :gis-change/user test-username}
                {:gis-change/op :INSERT
                 :gis-change/schema test-schema
                 :gis-change/table "subregion"
                 :gis-change/user test-username}
                {:gis-change/op :INSERT
                 :gis-change/schema test-schema
                 :gis-change/table "card_minimap_viewport"
                 :gis-change/user test-username}]
               (->> (db/with-db [admin-conn {}]
                      (gis-db/get-changes admin-conn))
                    (map #(select-keys % [:gis-change/op :gis-change/schema :gis-change/table :gis-change/user]))))))

      (testing "cannot view the master schema"
        (is (thrown-with-msg? PSQLException #"^ERROR: permission denied for schema test_territorybro"
                              (db/execute! user-conn [(str "select * from " (:database-schema config/env) ".congregation")]))))

      (testing "cannot create objects in the public schema"
        (is (thrown-with-msg? PSQLException #"^ERROR: permission denied for schema public"
                              (db/execute-one! user-conn ["create table public.foo (id serial primary key)"])))))

    (testing "cannot login to database after user is deleted"
      (db/with-db [admin-conn {}]
        (gis-db/ensure-user-absent! admin-conn {:username test-username
                                                :schema test-schema}))
      (is (thrown-with-msg? PSQLException (re-equals "FATAL: password authentication failed for user \"test_gis_user\"")
                            (with-open [user-conn (jdbc/get-connection user-db-spec)]
                              (db/execute-one! user-conn ["select 1 as test"])))))))

(deftest validate-grants-test
  (let [grants [{:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "territory", :privilege_type "SELECT"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "territory", :privilege_type "INSERT"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "territory", :privilege_type "UPDATE"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "territory", :privilege_type "DELETE"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "subregion", :privilege_type "SELECT"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "subregion", :privilege_type "INSERT"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "subregion", :privilege_type "UPDATE"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "subregion", :privilege_type "DELETE"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "congregation_boundary", :privilege_type "SELECT"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "congregation_boundary", :privilege_type "INSERT"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "congregation_boundary", :privilege_type "UPDATE"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "congregation_boundary", :privilege_type "DELETE"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "card_minimap_viewport", :privilege_type "SELECT"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "card_minimap_viewport", :privilege_type "INSERT"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "card_minimap_viewport", :privilege_type "UPDATE"}
                {:grantee "test_gis_user", :table_schema "test_gis_schema", :table_name "card_minimap_viewport", :privilege_type "DELETE"}]]
    (is (= {:username "test_gis_user"
            :schema "test_gis_schema"}
           (#'gis-db/validate-grants grants)))
    (is (nil? (#'gis-db/validate-grants (drop 1 grants))))
    (is (nil? (#'gis-db/validate-grants (assoc-in grants [0 :grantee] "foo"))))
    (is (nil? (#'gis-db/validate-grants (assoc-in grants [0 :privilege_type] "foo"))))
    (is (nil? (#'gis-db/validate-grants (assoc-in grants [0 :table_name] "foo"))))
    (is (nil? (#'gis-db/validate-grants (assoc-in grants [0 :table_schema] "foo"))))))

(deftest get-present-users-test
  (with-tenant-schema test-schema2
    (fn []
      (testing "lists GIS users present in the database"
        (db/with-db [conn {:rollback-only true}]
          (gis-db/ensure-user-present! conn {:username test-username
                                             :password "password"
                                             :schema test-schema})
          (gis-db/ensure-user-present! conn {:username test-username2
                                             :password "password"
                                             :schema test-schema})
          (gis-db/ensure-user-present! conn {:username test-username
                                             :password "password"
                                             :schema test-schema2})
          (is (= #{{:username test-username :schema test-schema}
                   {:username test-username2 :schema test-schema}
                   {:username test-username :schema test-schema2}}
                 (set (gis-db/get-present-users conn {:username-prefix test-username
                                                      :schema-prefix test-schema}))))))

      (testing "doesn't list GIS users with missing grants"
        (db/with-db [conn {:rollback-only true}]
          (gis-db/ensure-user-present! conn {:username test-username
                                             :password "password"
                                             :schema test-schema})
          (db/execute-one! conn [(str "REVOKE DELETE ON TABLE " test-schema ".territory FROM " test-username)])
          (is (= []
                 (gis-db/get-present-users conn {:username-prefix test-username
                                                 :schema-prefix test-schema}))))))))

(deftest get-present-schemas-test
  (with-tenant-schema test-schema2
    (fn []
      (testing "lists tenant schemas present in the database"
        (db/with-db [conn {}]
          (is (= [test-schema test-schema2]
                 (sort (gis-db/get-present-schemas conn {:schema-prefix test-schema}))))))

      (testing "doesn't list tenant schemas whose migrations are not up to date"
        (db/with-db [conn {}]
          (db/execute-one! conn [(format "UPDATE %s.flyway_schema_history SET checksum = 42 WHERE version = '1'" test-schema2)])
          (.commit ^Connection conn) ; commit so that Flyway will see the changes, since it uses a new DB connection
          (is (= [test-schema]
                 (sort (gis-db/get-present-schemas conn {:schema-prefix test-schema})))))))))
