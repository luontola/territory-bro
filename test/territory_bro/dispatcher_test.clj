;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dispatcher-test
  (:require [clojure.test :refer :all]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.event-store :as event-store]
            [territory-bro.infra.event-store-test :as event-store-test]
            [territory-bro.infra.user :as user]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [re-contains re-equals thrown-with-msg?]])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant)
           (java.util UUID)
           (territory_bro ValidationException WriteConflictException)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def test-time (Instant/ofEpochSecond 1))

(use-fixtures :once (fixed-clock-fixture test-time))

(deftest ^:slow write-stream!-test
  (with-fixtures [db-fixture event-store-test/bypass-validating-serializers]
    (let [write-stream! #'dispatcher/write-stream!
          append-stream! #'dispatcher/append-stream!
          stream-id (UUID/randomUUID)]

      (testing "appends events to the stream"
        (db/with-db [conn {}]
          (write-stream! conn stream-id (fn [old-events]
                                          (into [] old-events)
                                          [{:event/type :event-1
                                            :stuff "foo"}]))
          (write-stream! conn stream-id (fn [old-events]
                                          (into [] old-events) ; must read all events, or stream revision calculation will break
                                          [{:event/type :event-2
                                            :stuff "bar"}]))
          (is (= [:event-1 :event-2]
                 (into []
                       (map :event/type)
                       (event-store/read-stream conn stream-id))))))

      (testing "uses optimistic concurrency control to detect concurrent writes to the same stream"
        (db/with-db [conn {}]
          (is (thrown-with-msg?
               WriteConflictException #"Failed to save stream .*? revision 3: \{:event/type :event-4, :stuff \"conflicts\"\}"
               (write-stream! conn stream-id (fn [old-events]
                                               (into [] old-events)
                                               (db/with-db [conn {}]
                                                 (append-stream! conn stream-id [{:event/type :event-3
                                                                                  :stuff "concurrent write"}]))
                                               [{:event/type :event-4
                                                 :stuff "conflicts"}])))))
        (db/with-db [conn {}]
          (is (= [:event-1 :event-2 :event-3]
                 (into []
                       (map :event/type)
                       (event-store/read-stream conn stream-id)))))))))

(deftest call-command-handler-test
  (let [call! #'dispatcher/call!
        command {:command/type :dummy-command
                 :command/user user-id}
        state :dummy-state
        injections {}]

    (testing "calls the command handler"
      (let [*command-handler-args (atom nil)]
        (is (empty? (call! (fn [& args]
                             (reset! *command-handler-args args)
                             nil)
                           command state injections)))
        (is (= [command state injections]
               @*command-handler-args))))

    (testing "enriches the produced events"
      (is (= [{:event/type :congregation.event/congregation-renamed
               :event/time test-time ; added
               :event/user user-id ; added
               :congregation/id cong-id
               :congregation/name ""}]
             (call! (fn [& _]
                      [{:event/type :congregation.event/congregation-renamed
                        :congregation/id cong-id
                        :congregation/name ""}])
                    command state injections))))

    (testing "validates the produced events"
      (is (thrown-with-msg?
           ExceptionInfo (re-contains "Value does not match schema")
           (call! (fn [& _]
                    [{:event/type :congregation.event/congregation-renamed
                      :congregation/id cong-id}])
                  command state injections))))))

(deftest dispatch-command-test
  (let [conn :dummy-conn
        state {:territory-bro.domain.congregation/congregations {cong-id {}}}]
    (with-redefs [user/check-user-exists (fn [_conn id]
                                           (when-not (= id user-id)
                                             (throw (ValidationException. [[:no-such-user id]]))))
                  event-store/check-new-stream (fn [_conn id]
                                                 (when (= id (UUID. 0 0x666))
                                                   (throw (WriteConflictException. (str "stream " id)))))]

      (testing "dispatches commands"
        (let [command {:command/type :congregation.command/update-congregation
                       :command/time (Instant/now)
                       :command/user user-id
                       :congregation/id cong-id
                       :congregation/name ""}
              *spy (atom nil)]
          (with-redefs [dispatcher/command-handlers {"congregation.command" (fn [& args]
                                                                              (reset! *spy args)
                                                                              :dummy-return-value)}]
            (is (= :dummy-return-value
                   (dispatcher/command! conn state command))))
          (is (= [conn command state] @*spy))))

      (testing "validates commands"
        (let [command {:command/type :congregation.command/update-congregation}]
          (is (thrown-with-msg?
               ExceptionInfo (re-contains "Value does not match schema")
               (dispatcher/command! conn state command)))))

      (testing "validates foreign key references:"
        (testing "congregation"
          (let [command {:command/type :congregation.command/update-congregation
                         :command/time (Instant/now)
                         :command/user user-id
                         :congregation/id (UUID. 0 0x666)
                         :congregation/name ""}]
            (is (thrown-with-msg?
                 ValidationException (re-equals "[[:no-such-congregation #uuid \"00000000-0000-0000-0000-000000000666\"]]")
                 (dispatcher/command! conn state command)))))

        (testing "user"
          (let [command {:command/type :congregation.command/add-user
                         :command/time (Instant/now)
                         :command/user user-id
                         :congregation/id cong-id
                         :user/id (UUID. 0 0x666)}]
            (is (thrown-with-msg?
                 ValidationException (re-equals "[[:no-such-user #uuid \"00000000-0000-0000-0000-000000000666\"]]")
                 (dispatcher/command! conn state command)))))

        (testing "new stream"
          (let [command {:command/type :congregation.command/create-congregation
                         :command/time (Instant/now)
                         :command/user user-id
                         :congregation/id (UUID. 0 0x666)
                         :congregation/name "the name"}]
            (is (thrown-with-msg?
                 WriteConflictException (re-equals "stream 00000000-0000-0000-0000-000000000666")
                 (dispatcher/command! conn state command)))))))))
