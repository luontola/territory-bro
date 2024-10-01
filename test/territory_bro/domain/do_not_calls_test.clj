;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.domain.do-not-calls-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.infra.db :as db]
            [territory-bro.test.fixtures :refer [db-fixture]]
            [territory-bro.test.testutil :refer [thrown?]])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException)))

(use-fixtures :once db-fixture)

(def cong-id (UUID. 0 1))
(def territory-id (UUID. 0 2))

(deftest do-not-calls-test
  (db/with-transaction [conn {:rollback-only true}]
    (let [injections {:conn conn
                      :check-permit (fn [_permit])}
          state {}
          create-command {:command/type :do-not-calls.command/save-do-not-calls
                          :command/time (Instant/ofEpochSecond 1)
                          :congregation/id cong-id
                          :territory/id territory-id
                          :territory/do-not-calls "original text"}]
      (do-not-calls/handle-command (assoc create-command
                                          :command/time (Instant/ofEpochSecond 666)
                                          :congregation/id (UUID. 0 0x666)
                                          :territory/do-not-calls "unrelated 1")
                                   state
                                   injections)
      (do-not-calls/handle-command (assoc create-command
                                          :command/time (Instant/ofEpochSecond 666)
                                          :territory/id (UUID. 0 0x666)
                                          :territory/do-not-calls "unrelated 2")
                                   state
                                   injections)

      (testing "cannot read do-not-calls when there are none"
        (is (nil? (do-not-calls/get-do-not-calls conn cong-id territory-id))))

      (testing "create do-not-calls"
        (do-not-calls/handle-command create-command state injections)
        (is (= {:congregation/id cong-id
                :territory/id territory-id
                :territory/do-not-calls "original text"
                :do-not-calls/last-modified (Instant/ofEpochSecond 1)}
               (do-not-calls/get-do-not-calls conn cong-id territory-id))))

      (testing "update do-not-calls"
        (let [update-command (assoc create-command
                                    :command/time (Instant/ofEpochSecond 2)
                                    :territory/do-not-calls "updated text")]
          (do-not-calls/handle-command update-command state injections)
          (is (= {:congregation/id cong-id
                  :territory/id territory-id
                  :territory/do-not-calls "updated text"
                  :do-not-calls/last-modified (Instant/ofEpochSecond 2)}
                 (do-not-calls/get-do-not-calls conn cong-id territory-id)))))

      (testing "delete do-not-calls"
        (let [delete-command (assoc create-command
                                    :command/time (Instant/ofEpochSecond 3)
                                    :territory/do-not-calls "")]
          (do-not-calls/handle-command delete-command state injections)
          (is (nil? (do-not-calls/get-do-not-calls conn cong-id territory-id)))))

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
               (do-not-calls/get-do-not-calls conn cong-id (UUID. 0 0x666)))))

      (testing "checks write permits"
        (let [injections (assoc injections :check-permit (fn [permit]
                                                           (is (= [:edit-do-not-calls cong-id territory-id] permit))
                                                           (throw (NoPermitException. nil nil))))]
          (is (thrown? NoPermitException
                       (do-not-calls/handle-command create-command state injections))))))))

(defn fake-get-do-not-calls [_conn cong-id territory-id]
  {:congregation/id cong-id
   :territory/id territory-id
   :territory/do-not-calls "the do-not-calls"
   :do-not-calls/last-modified (Instant/now)})
