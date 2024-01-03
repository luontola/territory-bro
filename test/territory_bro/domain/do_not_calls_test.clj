;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.domain.do-not-calls-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.infra.db :as db]
            [territory-bro.test.fixtures :refer [db-fixture]])
  (:import (java.time Instant)
           (java.util UUID)))

(use-fixtures :once db-fixture)

(def cong-id (UUID. 0 1))
(def territory-id (UUID. 0 2))

(deftest do-not-calls-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)
    (do-not-calls/save-do-not-calls! conn {:command/time (Instant/ofEpochSecond 666)
                                           :congregation/id (UUID. 0 0x666)
                                           :territory/id territory-id
                                           :territory/do-not-calls "unrelated 1"})
    (do-not-calls/save-do-not-calls! conn {:command/time (Instant/ofEpochSecond 666)
                                           :congregation/id cong-id
                                           :territory/id (UUID. 0 0x666)
                                           :territory/do-not-calls "unrelated 2"})

    (testing "cannot read do-not-calls when there are none"
      (is (nil? (do-not-calls/get-do-not-calls conn cong-id territory-id))))

    (testing "create do-not-calls"
      (do-not-calls/save-do-not-calls! conn {:command/time (Instant/ofEpochSecond 1)
                                             :congregation/id cong-id
                                             :territory/id territory-id
                                             :territory/do-not-calls "original text"})
      (is (= {:congregation/id cong-id
              :territory/id territory-id
              :territory/do-not-calls "original text"
              :do-not-calls/last-modified (Instant/ofEpochSecond 1)}
             (do-not-calls/get-do-not-calls conn cong-id territory-id))))

    (testing "cannot read do-not-calls when the user lacks read permission") ; TODO

    (testing "update do-not-calls"
      (do-not-calls/save-do-not-calls! conn {:command/time (Instant/ofEpochSecond 2)
                                             :congregation/id cong-id
                                             :territory/id territory-id
                                             :territory/do-not-calls "updated text"})
      (is (= {:congregation/id cong-id
              :territory/id territory-id
              :territory/do-not-calls "updated text"
              :do-not-calls/last-modified (Instant/ofEpochSecond 2)}
             (do-not-calls/get-do-not-calls conn cong-id territory-id))))

    (testing "delete do-not-calls"
      (do-not-calls/save-do-not-calls! conn {:command/time (Instant/ofEpochSecond 3)
                                             :congregation/id cong-id
                                             :territory/id territory-id
                                             :territory/do-not-calls ""})
      (is (nil? (do-not-calls/get-do-not-calls conn cong-id territory-id))))

    (testing "did not modify unrelated database rows"
      (is (= {:congregation/id (UUID. 0 0x666)
              :territory/id territory-id
              :territory/do-not-calls "unrelated 1"
              :do-not-calls/last-modified (Instant/ofEpochSecond 666)}
             (do-not-calls/get-do-not-calls conn (UUID. 0 0x666) territory-id)))
      (is (= {:congregation/id cong-id
              :territory/id (UUID. 0 0x666)
              :territory/do-not-calls "unrelated 2"
              :do-not-calls/last-modified (Instant/ofEpochSecond 666)}
             (do-not-calls/get-do-not-calls conn cong-id (UUID. 0 0x666)))))))
