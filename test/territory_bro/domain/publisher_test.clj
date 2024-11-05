(ns ^:slow territory-bro.domain.publisher-test
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.infra.db :as db]
            [territory-bro.test.fixtures :refer [db-fixture]]
            [territory-bro.test.testutil :refer [re-equals thrown-with-msg?]])
  (:import (java.util UUID)
           (territory_bro ValidationException)))

(defn cache-fixture [f]
  (mount/start #'publisher/publishers-cache)
  (f))

(use-fixtures :once (join-fixtures [db-fixture cache-fixture]))

(deftest publishers-test
  (db/with-transaction [conn {:rollback-only true}]
    (let [cong-id (UUID/randomUUID)
          publisher-id (UUID/randomUUID)
          unrelated-1 {:congregation/id (UUID/randomUUID)
                       :publisher/id publisher-id
                       :publisher/name "Unrelated 1"}
          unrelated-2 {:congregation/id cong-id
                       :publisher/id (UUID/randomUUID)
                       :publisher/name "Unrelated 2"}]
      (publisher/save-publisher! conn unrelated-1)
      (publisher/save-publisher! conn unrelated-2)

      (testing "create a new publisher"
        (let [publisher {:congregation/id cong-id
                         :publisher/id publisher-id
                         :publisher/name "Publisher 1"}]
          (publisher/save-publisher! conn publisher)
          (is (= publisher (publisher/get-by-id conn cong-id publisher-id)))))

      (testing "rename publisher"
        (let [updated {:congregation/id cong-id
                       :publisher/id publisher-id
                       :publisher/name "new name"}]
          (publisher/save-publisher! conn updated)
          (is (= updated (publisher/get-by-id conn cong-id publisher-id)))))

      (testing "list all publishers in a congregation"
        (let [cong-id (UUID/randomUUID)
              publishers [{:congregation/id cong-id
                           :publisher/id (UUID/randomUUID)
                           :publisher/name "A"}
                          {:congregation/id cong-id
                           :publisher/id (UUID/randomUUID)
                           :publisher/name "B"}
                          {:congregation/id cong-id
                           :publisher/id (UUID/randomUUID)
                           :publisher/name "C"}]]
          (is (empty? (publisher/list-publishers conn cong-id)))
          (doseq [publisher publishers]
            (publisher/save-publisher! conn publisher))
          (is (= publishers (publisher/list-publishers conn cong-id)))))

      (testing "publisher not found"
        (is (nil? (publisher/get-by-id conn (UUID/randomUUID) publisher-id)))
        (is (nil? (publisher/get-by-id conn cong-id (UUID/randomUUID)))))

      (testing "did not accidentally change unrelated publishers"
        (is (= unrelated-1 (publisher/get-by-id conn (:congregation/id unrelated-1) (:publisher/id unrelated-1))))
        (is (= unrelated-2 (publisher/get-by-id conn (:congregation/id unrelated-2) (:publisher/id unrelated-2))))))))

(deftest publisher-names-test
  (db/with-transaction [conn {:rollback-only true}]

    (testing "publisher names are whitespace-normalized on save"
      (let [cong-id (UUID/randomUUID)
            publisher {:congregation/id cong-id
                       :publisher/id (UUID/randomUUID)
                       :publisher/name " \t John \u00a0  Doe \r\n"}]
        (publisher/save-publisher! conn publisher)
        (is (= "John Doe"
               (->> (publisher/list-publishers conn cong-id)
                    first
                    :publisher/name)))))

    (testing "publisher names must be unique within a congregation (case-insensitive)"
      (let [cong-id (UUID/randomUUID)
            publisher-id (UUID/randomUUID)
            publisher {:congregation/id cong-id
                       :publisher/id publisher-id
                       :publisher/name "foo BAR"}
            conflicting-publisher {:congregation/id cong-id
                                   :publisher/id (UUID/randomUUID)
                                   :publisher/name "FOO bar"}]
        (publisher/save-publisher! conn publisher)
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:non-unique-name]]")
             (publisher/save-publisher! conn conflicting-publisher)))
        (is (= [publisher]
               (publisher/list-publishers conn cong-id)))

        (testing "- allows changing a publisher's name's case"
          (let [publisher-v2 {:congregation/id cong-id
                              :publisher/id publisher-id
                              :publisher/name "Foo Bar"}]
            (publisher/save-publisher! conn publisher-v2)
            (is (= [publisher-v2]
                   (publisher/list-publishers conn cong-id)))))

        (testing "- allows the same name in other congregations"
          (let [other-cong-id (UUID/randomUUID)
                other-publisher {:congregation/id other-cong-id
                                 :publisher/id (UUID/randomUUID)
                                 :publisher/name "Foo Bar"}]
            (publisher/save-publisher! conn other-publisher)
            (is (= [other-publisher]
                   (publisher/list-publishers conn other-cong-id)))))))

    (testing "publisher names cannot be empty"
      (let [cong-id (UUID/randomUUID)
            publisher {:congregation/id cong-id
                       :publisher/id (UUID/randomUUID)
                       :publisher/name " "}]
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:missing-name]]")
             (publisher/save-publisher! conn publisher)))
        (is (empty? (publisher/list-publishers conn cong-id)))))))

(deftest check-publisher-exists-test
  (db/with-transaction [conn {:rollback-only true}]
    (let [cong-id (UUID. 0 1)
          publisher-id (UUID. 0 2)
          publisher {:congregation/id cong-id
                     :publisher/id publisher-id
                     :publisher/name "Publisher"}]
      (publisher/save-publisher! conn publisher)

      (testing "exists"
        (is (nil? (publisher/check-publisher-exists conn cong-id publisher-id))))

      (testing "doesn't exist"
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:no-such-publisher #uuid \"00000000-0000-0000-0000-000000000666\" #uuid \"00000000-0000-0000-0000-000000000002\"]]")
             (publisher/check-publisher-exists conn (UUID. 0 0x666) publisher-id)))
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:no-such-publisher #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000666\"]]")
             (publisher/check-publisher-exists conn cong-id (UUID. 0 0x666))))))))
