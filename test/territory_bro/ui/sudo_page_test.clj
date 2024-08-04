;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.sudo-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.ui.sudo-page :as sudo-page])
  (:import (clojure.lang ExceptionInfo)
           (java.util UUID)))

(deftest sudo-test
  (let [super-user-id (UUID. 0 1)
        regular-user-id (UUID. 0 2)
        request {}]
    (binding [config/env {:super-users #{super-user-id}}]

      (testing "super user is allowed"
        (auth/with-user-id super-user-id
          (is (= {:status 303
                  :headers {"Location" "/"}
                  :session {:territory-bro.api/sudo? true}
                  :body ""}
                 (sudo-page/sudo request)))))

      (testing "regular user is denied"
        (auth/with-user-id regular-user-id
          (is (thrown-match? ExceptionInfo
                             {:type :ring.util.http-response/response
                              :response {:status 403
                                         :body "Not super user"
                                         :headers {}}}
                             (sudo-page/sudo request)))))

      (testing "anonymous user is denied"
        (auth/with-anonymous-user
          (is (thrown-match? ExceptionInfo
                             {:type :ring.util.http-response/response
                              :response {:status 401
                                         :body "Not logged in"
                                         :headers {}}}
                             (sudo-page/sudo request))))))))
