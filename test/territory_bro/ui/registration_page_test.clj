;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.registration-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.registration-page :as registration-page])
  (:import (clojure.lang ExceptionInfo)))

(def model {})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          request {:session (auth/user-session {:name "John Doe"} user-id)}]

      (testing "logged in"
        (is (= model (registration-page/model! request))))

      (testing "anonymous user"
        (is (thrown-match? ExceptionInfo
                           {:type :ring.util.http-response/response
                            :response {:status 401
                                       :body "Not logged in"
                                       :headers {}}}
                           (registration-page/model! (dissoc request :session))))))))

(deftest ^:slow submit!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          request {:params {:congregationName "the name"}
                   :session (auth/user-session {:name "John Doe"} user-id)}]

      (testing "logged in"
        (with-fixtures [fake-dispatcher-fixture]
          (let [response (registration-page/submit! request)]
            (is (= {:command/type :congregation.command/create-congregation
                    :command/user user-id
                    :congregation/name "the name"}
                   (select-keys @*last-command [:command/type
                                                :command/user
                                                :congregation/name])))
            (is (= {:status 303
                    :headers {"Location" (str "/congregation/" (:congregation/id @*last-command))}
                    :body ""}
                   response)))))

      (testing "anonymous user"
        (with-fixtures [fake-dispatcher-fixture]
          (is (thrown-match? ExceptionInfo
                             {:type :ring.util.http-response/response
                              :response {:status 401
                                         :body "Not logged in"
                                         :headers {}}}
                             (registration-page/submit! (dissoc request :session)))))))))

(deftest view-test
  (is (= (html/normalize-whitespace
          "Register a new congregation

           Congregation name []
           Register

           We recommend subscribing to our mailing list to be notified about important Territory Bro updates.")
         (-> (registration-page/view model)
             html/visible-text))))
