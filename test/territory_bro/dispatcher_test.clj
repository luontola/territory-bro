;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dispatcher-test
  (:require [clojure.test :refer :all]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.event-store :as event-store]
            [territory-bro.testutil :refer [re-equals re-contains]]
            [territory-bro.user :as user])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant)
           (java.util UUID)
           (territory_bro ValidationException WriteConflictException)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def test-time (Instant/ofEpochSecond 1))

(deftest call-command-handler-test
  (let [call! #'dispatcher/call!
        command {:command/type :dummy-command
                 :command/user user-id}
        state :dummy-state
        injections {:now (constantly test-time)}]

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
               :event/version 1
               :congregation/id cong-id
               :congregation/name ""}]
             (call! (fn [& _]
                      [{:event/type :congregation.event/congregation-renamed
                        :event/version 1
                        :congregation/id cong-id
                        :congregation/name ""}])
                    command state injections))))

    (testing "validates the produced events"
      (is (thrown-with-msg?
           ExceptionInfo (re-contains "Value does not match schema")
           (call! (fn [& _]
                    [{:event/type :congregation.event/congregation-renamed
                      :event/version 1
                      :congregation/id cong-id}])
                  command state injections))))))

(deftest dispatch-command-test
  (let [conn :dummy-conn
        state {:territory-bro.congregation/congregations {cong-id {}}}]
    (with-redefs [user/check-user-exists (fn [_conn id]
                                           (when-not (= id user-id)
                                             (throw (ValidationException. [[:no-such-user id]]))))
                  event-store/check-new-stream (fn [_conn id]
                                                 (when (= id (UUID. 0 0x666))
                                                   (throw (WriteConflictException. (str "stream " id)))))]

      (testing "dispatches commands"
        (let [command {:command/type :congregation.command/rename-congregation
                       :command/time (Instant/now)
                       :command/user user-id
                       :congregation/id cong-id
                       :congregation/name ""}
              *spy (atom nil)]
          (with-redefs [dispatcher/congregation-command! (fn [& args]
                                                           (reset! *spy args)
                                                           :dummy-return-value)]
            (is (= :dummy-return-value
                   (dispatcher/command! conn state command))))
          (is (= [conn command state] @*spy))))

      (testing "validates commands"
        (let [command {:command/type :congregation.command/rename-congregation}]
          (is (thrown-with-msg?
               ExceptionInfo (re-contains "Value does not match schema")
               (dispatcher/command! conn state command)))))

      (testing "validates foreign key references:"
        (testing "congregation"
          (let [command {:command/type :congregation.command/rename-congregation
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
