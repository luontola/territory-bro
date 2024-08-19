;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.sudo-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.infra.config :as config]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.ui.sudo-page :as sudo-page])
  (:import (clojure.lang ExceptionInfo)
           (java.util UUID)))

(deftest sudo-test
  (let [super-user-id (UUID. 0 1)
        regular-user-id (UUID. 0 2)
        request {}]
    (binding [config/env {:super-users #{super-user-id}}]

      (testing "super user is allowed"
        (testutil/with-user-id super-user-id
          (is (= {:status 303
                  :headers {"Location" "/"}
                  :session {::dmz/sudo? true}
                  :body ""}
                 (sudo-page/sudo request)))))

      (testing "regular user is denied"
        (testutil/with-user-id regular-user-id
          (is (thrown-match? ExceptionInfo dmz-test/access-denied
                             (sudo-page/sudo request)))))

      (testing "anonymous user is denied"
        (testutil/with-anonymous-user
          (is (thrown-match? ExceptionInfo dmz-test/not-logged-in
                             (sudo-page/sudo request))))))))
