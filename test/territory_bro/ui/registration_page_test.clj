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

(deftest view-test
  (is (= (html/normalize-whitespace
          "Register a new congregation

           Congregation name
           Register

           We recommend subscribing to our mailing list to be notified about important Territory Bro updates.")
         (-> (registration-page/view model)
             html/visible-text))))
