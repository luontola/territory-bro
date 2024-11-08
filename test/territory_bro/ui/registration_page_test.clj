(ns territory-bro.ui.registration-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.registration-page :as registration-page])
  (:import (clojure.lang ExceptionInfo)
           (territory_bro ValidationException)))

(def model {:form nil})

(deftest model!-test
  (let [user-id (random-uuid)
        request {}]

    (testing "logged in"
      (testutil/with-user-id user-id
        (is (= model (registration-page/model! request)))))

    (testing "anonymous"
      (testutil/with-anonymous-user
        (is (thrown-match? ExceptionInfo dmz-test/not-logged-in
                           (registration-page/model! request)))))))

(deftest submit!-test
  (let [user-id (random-uuid)
        request {:params {:congregationName "the name"}}]
    (testutil/with-user-id user-id
      (testing "registration ok"
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

      (testing "registration failed: highlights erroneous form fields, doesn't forget invalid user input"
        (binding [dispatcher/command! (fn [& _]
                                        (throw (ValidationException. [[:missing-name]])))]
          (let [response (registration-page/submit! request)]
            (is (= forms/validation-error-http-status (:status response)))
            (is (str/includes?
                 (html/visible-text (:body response))
                 (html/normalize-whitespace
                  "Congregation name [the name] ⚠️
                   Register")))))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (thrown-match? ExceptionInfo dmz-test/not-logged-in
                           (registration-page/submit! request)))))))

(deftest view-test
  (is (= (html/normalize-whitespace
          "Register a new congregation

           Congregation name []
           Register

           or
           Join an existing congregation")
         (-> (registration-page/view model)
             html/visible-text))))
