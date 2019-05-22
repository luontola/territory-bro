;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.events-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [territory-bro.events :as events]
            [territory-bro.testutil :refer [re-equals re-contains]])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant)
           (java.util UUID)))

(def valid-event {:event/type :congregation.event/congregation-created
                  :event/version 1
                  :event/time (Instant/now)
                  :congregation/id (UUID/randomUUID)
                  :congregation/name ""
                  :congregation/schema-name ""})
(def invalid-event (dissoc valid-event :congregation/name))
(def unknown-event (assoc valid-event :event/type :foo))

(deftest test-event-schema
  (testing "check specific event schema"
    (is (nil? (s/check events/CongregationCreatedEvent valid-event))))

  (testing "check generic event schema"
    (is (nil? (s/check events/Event valid-event))))

  (testing "invalid event"
    (is (= {:congregation/name 'missing-required-key}
           (s/check events/Event invalid-event))))

  (testing "unknown event type"
    ;; TODO: produce a helpful error message
    (is (s/check events/Event unknown-event))))

(deftest test-validate-event
  (testing "valid event"
    (is (= valid-event (events/validate-event valid-event))))

  (testing "invalid event"
    (is (thrown-with-msg? ExceptionInfo (re-contains "#:congregation{:name missing-required-key}")
                          (events/validate-event invalid-event))))

  (testing "unknown event type"
    (is (thrown-with-msg? ExceptionInfo (re-equals "Unknown event type :foo")
                          (events/validate-event unknown-event)))))

(deftest test-validate-events
  (is (= [] (events/validate-events [])))
  (is (= [valid-event] (events/validate-events [valid-event])))
  (is (thrown? ExceptionInfo (events/validate-events [invalid-event]))))
