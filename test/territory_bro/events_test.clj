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

(deftest event-defaults-test
  (let [time (Instant/now)
        user (UUID/randomUUID)
        system "some-subsystem"]

    (testing "no context (not really allowed)"
      (is (= {:event/time time
              :event/version 1}
             (events/defaults time))))

    (testing "user context"
      (is (= {:event/time time
              :event/version 1
              :event/user user}
             (binding [events/*current-user* user]
               (events/defaults time)))))

    (testing "system context"
      (is (= {:event/time time
              :event/version 1
              :event/system system}
             (binding [events/*current-system* system]
               (events/defaults time)))))

    (testing "user and system context (not really allowed)"
      (is (= {:event/time time
              :event/version 1
              :event/user user
              :event/system system}
             (binding [events/*current-user* user
                       events/*current-system* system]
               (events/defaults time)))))))

(def valid-event {:event/type :congregation.event/congregation-created
                  :event/version 1
                  :event/time (Instant/now)
                  :event/user (UUID/randomUUID)
                  :congregation/id (UUID/randomUUID)
                  :congregation/name ""
                  :congregation/schema-name ""})
(def invalid-event (dissoc valid-event :congregation/name))
(def unknown-event (assoc valid-event :event/type :foo))

(deftest sorted-keys-test
  (is (= [:event/type
          :event/version
          :event/user
          :event/time
          :congregation/id
          :congregation/name
          :congregation/schema-name]
         (keys (events/sorted-keys valid-event)))))

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

;;; Generators for serialization tests

(def uuid-gen (gen/fmap (fn [[a b]]
                          (UUID. a b))
                        (gen/tuple gen/large-integer gen/large-integer)))
(def instant-gen (gen/fmap (fn [millis]
                             (Instant/ofEpochMilli millis))
                           (gen/large-integer* {:min 0})))
(def leaf-generators {UUID uuid-gen
                      Instant instant-gen})
(def event-user-gen (gen/tuple (gen/elements [:event/user])
                               uuid-gen))
(def event-system-gen (gen/tuple (gen/elements [:event/system])
                                 gen/string-alphanumeric))
(def dumb-event-gen (gen/one-of (->> (vals events/event-schemas)
                                     (map #(sg/generator % leaf-generators)))))
(def event-gen (gen/fmap (fn [[event [k v]]]
                           (-> event
                               (dissoc :event/user :event/system)
                               (assoc k v)))
                         (gen/tuple dumb-event-gen
                                    (gen/one-of [event-user-gen event-system-gen]))))

(deftest event-serialization-test
  (testing "round trip serialization"
    (doseq [event (gen/sample event-gen 100)]
      (is (= event (-> event events/event->json events/json->event)))))

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
