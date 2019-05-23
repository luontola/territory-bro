;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.events-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [schema-generators.generators :as sg]
            [schema.core :as s]
            [territory-bro.events :as events]
            [territory-bro.testutil :refer [re-equals re-contains]]
            [clojure.string :as str])
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

(deftest event-schema-test
  (testing "check specific event schema"
    (is (nil? (s/check events/CongregationCreated valid-event))))

  (testing "check generic event schema"
    (is (nil? (s/check events/Event valid-event))))

  (testing "invalid event"
    (is (= {:congregation/name 'missing-required-key}
           (s/check events/Event invalid-event))))

  (testing "unknown event type"
    ;; TODO: produce a helpful error message
    (is (s/check events/Event unknown-event))))

(deftest validate-event-test
  (testing "valid event"
    (is (= valid-event (events/validate-event valid-event))))

  (testing "invalid event"
    (is (thrown-with-msg? ExceptionInfo (re-contains "#:congregation{:name missing-required-key}")
                          (events/validate-event invalid-event))))

  (testing "unknown event type"
    (is (thrown-with-msg? ExceptionInfo (re-equals "Unknown event type :foo")
                          (events/validate-event unknown-event)))))

(deftest validate-events-test
  (is (= [] (events/validate-events [])))
  (is (= [valid-event] (events/validate-events [valid-event])))
  (is (thrown? ExceptionInfo (events/validate-events [invalid-event]))))

(deftest event-serialization-test
  (testing "round trip serialization"
    (let [generators {UUID (gen/fmap (fn [[a b]]
                                       (UUID. a b))
                                     (gen/tuple gen/large-integer gen/large-integer))
                      Instant (gen/fmap (fn [millis]
                                          (Instant/ofEpochMilli millis))
                                        (gen/large-integer* {:min 0}))}]
      (doseq [event (sg/sample 100 events/Event generators)]
        (is (= event (-> event events/event->json events/json->event))))))

  (testing "event->json validates events"
    (is (thrown-with-msg? ExceptionInfo (re-equals "Unknown event type nil")
                          (events/event->json {})))
    (is (thrown-with-msg? ExceptionInfo (re-contains "Value does not match schema")
                          (events/event->json invalid-event))))

  (testing "json->event validates events"
    (is (thrown-with-msg? ExceptionInfo #"Event schema validation failed"
                          (events/json->event "{}"))))

  (testing "json data format"
    (let [event (assoc valid-event
                       :event/time (Instant/ofEpochMilli 1))
          json (events/event->json event)]
      (is (str/includes? json "\"event/time\":\"1970-01-01T00:00:00.001Z\"")))))
