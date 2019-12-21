;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dispatcher-test
  (:require [clojure.test :refer :all]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.testutil :refer [re-contains]])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def test-time (Instant/ofEpochSecond 1))

(deftest call-command-handler-test
  (let [command {:command/type :dummy-command
                 :command/user user-id}
        state :dummy-state
        injections {:now (constantly test-time)}]

    (testing "calls the command handler"
      (let [*command-handler-args (atom nil)]
        (is (empty? (dispatcher/call! (fn [& args]
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
             (dispatcher/call! (fn [& _]
                                 [{:event/type :congregation.event/congregation-renamed
                                   :event/version 1
                                   :congregation/id cong-id
                                   :congregation/name ""}])
                               command state injections))))

    (testing "validates the produced events"
      (is (thrown-with-msg?
           ExceptionInfo (re-contains "Value does not match schema")
           (dispatcher/call! (fn [& _]
                               [{:event/type :congregation.event/congregation-renamed
                                 :event/version 1
                                 :congregation/id cong-id}])
                             command state injections))))))

(deftest dispatch-command-test
  (testing "dispatches commands"
    (let [conn :dummy-conn
          state :dummy-state
          command {:command/type :congregation.command/rename-congregation
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
    (let [conn :dummy-conn
          state :dummy-state
          command {:command/type :congregation.command/rename-congregation}]
      (is (thrown-with-msg?
           ExceptionInfo (re-contains "Value does not match schema")
           (dispatcher/command! conn state command))))))
