(ns territory-bro.events-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [schema-generators.generators :as sg]
            [schema.core :as s]
            [territory-bro.events :as events]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [re-contains re-equals thrown-with-msg? thrown?]])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant LocalDate)
           (java.util UUID)))

(def test-time (Instant/now))

(use-fixtures :once (fixed-clock-fixture test-time))


(deftest enrich-events-test
  (let [user-id (UUID/randomUUID)
        system "some-subsystem"
        events [{:extra-keys :foo}]]

    (testing "no context (not really allowed)"
      (is (= [{:event/time test-time
               :extra-keys :foo}]
             (events/enrich-events {} events))))

    (testing "user context"
      (is (= [{:event/time test-time
               :event/user user-id
               :extra-keys :foo}]
             (events/enrich-events {:command/user user-id} events))))

    (testing "system context"
      (is (= [{:event/time test-time
               :event/system system
               :extra-keys :foo}]
             (events/enrich-events {:command/system system} events))))

    (testing "user and system context (not really allowed)"
      (is (= [{:event/time test-time
               :event/user user-id
               :event/system system
               :extra-keys :foo}]
             (events/enrich-events {:command/system system, :command/user user-id} events))))))

(def valid-event {:event/type :congregation.event/congregation-created
                  :event/time (Instant/now)
                  :event/user (UUID/randomUUID)
                  :congregation/id (UUID/randomUUID)
                  :congregation/name ""
                  :congregation/schema-name ""})
(def lax-event (dissoc valid-event :event/time))
(def invalid-event (dissoc valid-event :congregation/name))
(def unknown-event (assoc valid-event :event/type :foo))

(deftest sorted-keys-test
  (is (nil? (events/sorted-keys nil)))
  (is (= [:event/type
          :event/user
          :event/time
          :congregation/id
          :congregation/name
          :congregation/schema-name]
         (keys (events/sorted-keys valid-event)))))

;; TODO: deduplicate event & command validation infra

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
    (is (thrown-with-msg? ExceptionInfo (re-contains "{:congregation/name missing-required-key}")
                          (events/validate-event invalid-event))))

  (testing "basic validation accepts lax events"
    (is (= lax-event (events/validate-event lax-event))))

  (testing "strict validation rejects lax events"
    (is (thrown-with-msg? ExceptionInfo (re-contains "{:event/time missing-required-key}")
                          (events/strict-validate-event lax-event))))

  (testing "unknown event type"
    (is (thrown-with-msg? ExceptionInfo (re-equals "Unknown event type :foo")
                          (events/validate-event unknown-event)))))

(deftest validate-events-test
  (testing "no events"
    (is (= []
           (events/validate-events [])
           (events/strict-validate-events []))))

  (testing "valid event"
    (is (= [valid-event]
           (events/validate-events [valid-event])
           (events/strict-validate-events [valid-event]))))

  (testing "lax event"
    (is (= [lax-event] (events/validate-events [lax-event])))
    (is (thrown? ExceptionInfo (events/strict-validate-events [lax-event]))))

  (testing "invalid event"
    (is (thrown? ExceptionInfo (events/validate-events [invalid-event])))
    (is (thrown? ExceptionInfo (events/strict-validate-events [invalid-event])))))


;;;; Generators for serialization tests

(def uuid-gen (gen/fmap (fn [[a b]]
                          (UUID. a b))
                        (gen/tuple gen/large-integer gen/large-integer)))
(def instant-gen (gen/fmap (fn [millis]
                             (Instant/ofEpochMilli millis))
                           (gen/large-integer* {:min 0})))
(def date-gen (gen/fmap (fn [day]
                          (LocalDate/ofEpochDay day))
                        (gen/large-integer* {:min 0, :max (-> (LocalDate/now) (.plusYears 100) .toEpochDay)})))
(def leaf-generators {UUID uuid-gen
                      Instant instant-gen
                      LocalDate date-gen})
(def event-user-gen (gen/tuple (gen/elements [:event/user])
                               uuid-gen))
(def event-system-gen (gen/tuple (gen/elements [:event/system])
                                 gen/string-alphanumeric))
(def lax-event-gen (gen/one-of (->> (vals events/event-schemas)
                                    (map #(sg/generator % leaf-generators)))))
(def strict-event-gen
  (gen/fmap (fn [[event time [k v]]]
              ;; add required keys for strict validation
              (-> event
                  (assoc :event/time time)
                  (dissoc :event/user :event/system)
                  (assoc k v)))
            (gen/tuple lax-event-gen
                       instant-gen
                       (gen/one-of [event-user-gen event-system-gen]))))

(deftest ^:slow event-serialization-gen-test
  (testing "round trip serialization"
    (doseq [event (gen/sample strict-event-gen 100)]
      (is (= event (-> event events/event->json events/json->event))))))

(deftest event-serialization-test
  (testing "round trip serialization"
    (is (= valid-event (-> valid-event events/event->json events/json->event))))

  (testing "event->json validates events"
    (is (thrown-with-msg? ExceptionInfo (re-equals "Unknown event type nil")
                          (events/event->json {})))
    (is (thrown-with-msg? ExceptionInfo (re-contains "Value does not match schema")
                          (events/event->json invalid-event))))

  (testing "json->event validates events"
    (is (thrown-with-msg? ExceptionInfo (re-contains "Value cannot be coerced to match schema")
                          (events/json->event "{}"))))

  (testing "json data format"
    (let [event (assoc valid-event
                       :event/time (Instant/ofEpochMilli 1))
          json (events/event->json event)]
      (is (str/includes? json "\"event/time\":\"1970-01-01T00:00:00.001Z\"")))))
