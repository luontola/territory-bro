;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.registration-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.registration-page :as registration-page])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant)
           (java.util UUID)
           (territory_bro ValidationException)))

(def model {:form nil})

(deftest model!-test
  (let [user-id (UUID/randomUUID)
        request {}]
    (auth/with-user-id user-id

      (testing "logged in"
        (is (= model (registration-page/model! request))))

      (testing "anonymous user"
        (auth/with-anonymous-user
          (is (thrown-match? ExceptionInfo
                             {:type :ring.util.http-response/response
                              :response {:status 401
                                         :body "Not logged in"
                                         :headers {}}}
                             (registration-page/model! request))))))))

(deftest submit!-test
  (let [user-id (UUID/randomUUID)
        request {:params {:congregationName "the name"}}]
    (binding [config/env {:now #(Instant/now)}]
      (auth/with-user-id user-id

        (testing "logged in"
          (with-fixtures [fake-dispatcher-fixture]
            (let [response (registration-page/submit! request)]
              (is (= {:command/type :congregation.command/create-congregation
                      :command/user user-id
                      :congregation/name "the name"}
                     (dissoc @*last-command :command/time :congregation/id)))
              (is (= {:status 303
                      :headers {"Location" (str "/congregation/" (:congregation/id @*last-command))}
                      :body ""}
                     response)))))

        (testing "anonymous user"
          (auth/with-anonymous-user
            (with-fixtures [fake-dispatcher-fixture]
              (is (thrown-match? ExceptionInfo
                                 {:type :ring.util.http-response/response
                                  :response {:status 401
                                             :body "Not logged in"
                                             :headers {}}}
                                 (registration-page/submit! request))))))

        (testing "add failed: highlights erroneous form fields, doesn't forget invalid user input"
          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:missing-name]])))]
            (let [response (registration-page/submit! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "Congregation name [the name] ⚠️
                     Register"))))))))))

(deftest view-test
  (is (= (html/normalize-whitespace
          "Register a new congregation

           Congregation name []
           Register

           We recommend subscribing to our mailing list to be notified about important Territory Bro updates.")
         (-> (registration-page/view model)
             html/visible-text))))
