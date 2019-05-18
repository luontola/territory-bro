;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.event-store-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.fixtures :refer [db-fixture]])
  (:import (java.util UUID)))

(use-fixtures :once db-fixture)

(deftest gis-change-log-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [stream-1 (UUID/randomUUID)
          stream-2 (UUID/randomUUID)
          events [{:event/type :event-1
                   :stuff "foo"}
                  {:event/type :event-2
                   :stuff "bar"}]]
      (event-store/save! conn stream-1 0 events)
      (event-store/save! conn stream-2 0 events)

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
               (event-store/read-stream conn stream-1))))

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
               (event-store/read-all-events conn))))

      (testing "read all events since revision"
        (is (= [{:event/stream-id stream-2
                 :event/stream-revision 2
                 :event/global-revision 4
                 :event/type :event-2
                 :stuff "bar"}]
               (event-store/read-all-events conn {:since 3}))))

      (testing "append to stream") ; TODO

      (testing "error: expected revision too low") ; TODO

      (testing "error: expected revision too high")))) ; TODO
