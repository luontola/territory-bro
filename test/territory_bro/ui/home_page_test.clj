;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.home-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.home-page :as home-page]
            [territory-bro.ui.html :as html])
  (:import (java.util UUID)))

(def model
  {:congregations [{:id (UUID. 0 1)
                    :name "Congregation 1"}
                   {:id (UUID. 0 2)
                    :name "Congregation 2"}]})
(def anonymous-model
  {:congregations []})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id1 (at/create-congregation! session "Congregation 1")
          cong-id2 (at/create-congregation! session "Congregation 2")]

      (testing "logged in, with congregations"
        (is (= (-> model
                   (replace-in [:congregations 0 :id] (UUID. 0 1) cong-id1)
                   (replace-in [:congregations 1 :id] (UUID. 0 2) cong-id2))
               (home-page/model! {:session {::auth/user {:user/id user-id}}}))))

      (testing "anonymous user"
        (is (= anonymous-model
               (home-page/model! {})))))))

(deftest view-test
  (testing "logged in, with congregations"
    (is (= (html/normalize-whitespace
            "Territory Bro

             Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.
             See territorybro.com for more information.

             Your congregations
               Congregation 1
               Congregation 2")
           (-> (home-page/view model)
               html/visible-text))))

  (testing "anonymous user"
    (is (= (html/normalize-whitespace
            "Territory Bro

             Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.
             See territorybro.com for more information.")
           (-> (home-page/view anonymous-model)
               html/visible-text)))))
