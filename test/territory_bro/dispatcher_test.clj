;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dispatcher-test
  (:require [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.testutil :refer [re-contains]])
  (:import (java.util UUID)
           (java.time Instant)
           (clojure.lang ExceptionInfo)))

(deftest dispatch-command-test
  (testing "dispatches commands"
    (let [conn :dummy-conn
          state :dummy-state
          command {:command/type :congregation.command/rename-congregation
                   :command/time (Instant/now)
                   :command/user (UUID. 0 1)
                   :congregation/id (UUID. 0 2)
                   :congregation/name ""}
          *spy (atom nil)]
      (with-redefs [congregation/command! (fn [& args]
                                            (reset! *spy args))]
        (is (nil? (dispatcher/command! conn state command))))
      (is (= [conn command state] @*spy))))

  (testing "validates commands"
    (let [conn :dummy-conn
          state :dummy-state
          command {:command/type :congregation.command/rename-congregation}]
      (is (thrown-with-msg? ExceptionInfo (re-contains "Value does not match schema")
                            (dispatcher/command! conn state command))))))
