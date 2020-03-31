;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.event-store-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.json :as json]
            [territory-bro.testutil :refer [re-equals re-contains grab-exception]])
  (:import (clojure.lang ExceptionInfo)
           (java.util UUID)
           (org.postgresql.util PSQLException)
           (territory_bro WriteConflictException)))

(use-fixtures :once db-fixture)

(defn event->json-no-validate [event]
  (json/write-value-as-string event))

(defn json->event-no-validate [json]
  (-> (json/read-value json)
      (update :event/type keyword)))

(deftest event-store-test
  ;; bypass validating serializers
  (binding [event-store/*event->json* event->json-no-validate
            event-store/*json->event* json->event-no-validate]
    (db/with-db [conn {}]
      (jdbc/db-set-rollback-only! conn)

      (let [stream-1 (UUID/randomUUID)
            stream-2 (UUID/randomUUID)
            events [{:event/type :event-1
                     :stuff "foo"}
                    {:event/type :event-2
                     :stuff "bar"}]]

        (testing "create new stream"
          (is (= [{:event/stream-id stream-1
                   :event/stream-revision 1
                   :event/global-revision 1
                   :event/type :event-1
                   :stuff "foo"}
                  {:event/stream-id stream-1
                   :event/stream-revision 2
                   :event/global-revision 2
                   :event/type :event-2
                   :stuff "bar"}]
                 (event-store/save! conn stream-1 0 events)))
          (is (= [{:event/stream-id stream-2
                   :event/stream-revision 1 ; is a stream specific counter
                   :event/global-revision 3 ; is a global counter
                   :event/type :event-1
                   :stuff "foo"}
                  {:event/stream-id stream-2
                   :event/stream-revision 2
                   :event/global-revision 4
                   :event/type :event-2
                   :stuff "bar"}]
                 (event-store/save! conn stream-2 0 events))))

        (testing "read stream"
          (is (= [{:event/stream-id stream-1
                   :event/stream-revision 1
                   :event/global-revision 1
                   :event/type :event-1
                   :stuff "foo"}
                  {:event/stream-id stream-1
                   :event/stream-revision 2
                   :event/global-revision 2
                   :event/type :event-2
                   :stuff "bar"}]
                 (event-store/read-stream conn stream-1)
                 (event-store/read-stream conn stream-1 {:since 0})
                 (event-store/read-stream conn stream-1 {:since nil}))))

        (testing "read stream since revision"
          (is (= [{:event/stream-id stream-1
                   :event/stream-revision 2
                   :event/global-revision 2
                   :event/type :event-2
                   :stuff "bar"}]
                 (event-store/read-stream conn stream-1 {:since 1}))))

        (testing "read all events"
          (is (= [{:event/stream-id stream-1
                   :event/stream-revision 1
                   :event/global-revision 1
                   :event/type :event-1
                   :stuff "foo"}
                  {:event/stream-id stream-1
                   :event/stream-revision 2
                   :event/global-revision 2
                   :event/type :event-2
                   :stuff "bar"}
                  {:event/stream-id stream-2
                   :event/stream-revision 1
                   :event/global-revision 3
                   :event/type :event-1
                   :stuff "foo"}
                  {:event/stream-id stream-2
                   :event/stream-revision 2
                   :event/global-revision 4
                   :event/type :event-2
                   :stuff "bar"}]
                 (event-store/read-all-events conn)
                 (event-store/read-all-events conn {:since 0})
                 (event-store/read-all-events conn {:since nil}))))

        (testing "read all events since revision"
          (is (= [{:event/stream-id stream-2
                   :event/stream-revision 2
                   :event/global-revision 4
                   :event/type :event-2
                   :stuff "bar"}]
                 (event-store/read-all-events conn {:since 3}))))

        (testing "append to stream"
          (testing "with concurrency check"
            (is (= [{:event/stream-id stream-1
                     :event/stream-revision 3
                     :event/global-revision 5
                     :event/type :event-3
                     :stuff "gazonk"}]
                   (event-store/save! conn stream-1 2 [{:event/type :event-3
                                                        :stuff "gazonk"}]))))
          (testing "without concurrency check"
            (is (= [{:event/stream-id stream-1
                     :event/stream-revision 4
                     :event/global-revision 6
                     :event/type :event-4
                     :stuff "gazonk"}]
                   (event-store/save! conn stream-1 nil [{:event/type :event-4
                                                          :stuff "gazonk"}]))))
          (is (= [{:event/stream-id stream-1
                   :event/stream-revision 3
                   :event/global-revision 5
                   :event/type :event-3
                   :stuff "gazonk"}
                  {:event/stream-id stream-1
                   :event/stream-revision 4
                   :event/global-revision 6
                   :event/type :event-4
                   :stuff "gazonk"}]
                 (event-store/read-stream conn stream-1 {:since 2}))))))

    (db/with-db [conn {}]
      (jdbc/db-set-rollback-only! conn)

      (testing "error: expected revision too low"
        (let [stream-id (UUID. 0 0x123)]
          (event-store/save! conn stream-id 0 [{:event/type :event-1}])
          (let [^Exception exception (grab-exception
                                       (event-store/save! conn stream-id 0 [{:event/type :event-2}]))]

            (testing "(exception)"
              (is (instance? WriteConflictException exception))
              (is (= "Failed to save stream 00000000-0000-0000-0000-000000000123 revision 1: {:event/type :event-2}"
                     (.getMessage exception))))

            (testing "(cause)"
              (let [^PSQLException cause (.getCause exception)]
                (is (str/starts-with?
                     (.getMessage cause)
                     (str "ERROR: tried to insert stream revision 1 but it should have been 2\n"
                          "  Hint: The transaction might succeed if retried.")))
                (is (= db/psql-serialization-failure (.getSQLState cause)))))))))

    (db/with-db [conn {}]
      (jdbc/db-set-rollback-only! conn)

      (testing "error: expected revision too high"
        (let [stream-id (UUID. 0 0x123)]
          (event-store/save! conn stream-id 0 [{:event/type :event-1}])
          (let [^Exception exception (grab-exception
                                       (event-store/save! conn stream-id 2 [{:event/type :event-2}]))]

            (testing "(exception)"
              (is (instance? WriteConflictException exception))
              (is (= "Failed to save stream 00000000-0000-0000-0000-000000000123 revision 3: {:event/type :event-2}"
                     (.getMessage exception))))

            (testing "(cause)"
              (let [^PSQLException cause (.getCause exception)]
                (is (str/starts-with?
                     (.getMessage cause)
                     (str "ERROR: tried to insert stream revision 3 but it should have been 2\n"
                          "  Hint: The transaction might succeed if retried.")))
                (is (= db/psql-serialization-failure (.getSQLState cause)))))))))))

(deftest event-validation-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)
    (let [stream-id (UUID/randomUUID)]

      (testing "validates events on write"
        (is (thrown-with-msg? ExceptionInfo (re-equals "Unknown event type :dummy-event")
                              (event-store/save! conn stream-id 0 [{:event/type :dummy-event}]))))

      ;; bypass validating serializers
      (binding [event-store/*event->json* event->json-no-validate]
        (event-store/save! conn stream-id 0 [{:event/type :dummy-event}]))

      (testing "validates events on reading a stream"
        (is (thrown-with-msg? ExceptionInfo (re-contains "Value cannot be coerced to match schema")
                              (event-store/read-stream conn stream-id))))

      (testing "validates events on reading all events"
        (is (thrown-with-msg? ExceptionInfo (re-contains "Value cannot be coerced to match schema")
                              (event-store/read-all-events conn stream-id)))))))

(deftest check-event-stream-does-not-exist-test
  ;; bypass validating serializers
  (binding [event-store/*event->json* event->json-no-validate
            event-store/*json->event* json->event-no-validate]
    (db/with-db [conn {}]
      (jdbc/db-set-rollback-only! conn)
      (let [existing-stream-id (UUID. 0 1)
            non-existing-stream-id (UUID. 0 2)]
        (event-store/save! conn existing-stream-id 0 [{:event/type :dummy-event}])

        (testing "stream does not exist"
          (is (nil? (event-store/check-new-stream conn non-existing-stream-id))))

        (testing "stream exists"
          (is (thrown-with-msg?
               WriteConflictException (re-equals "Event stream 00000000-0000-0000-0000-000000000001 already exists")
               (event-store/check-new-stream conn existing-stream-id))))))))
