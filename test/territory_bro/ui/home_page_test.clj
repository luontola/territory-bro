;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.home-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.home-page :as home-page]
            [territory-bro.ui.html :as html])
  (:import (java.util UUID)))

(def model
  {:congregations [{:id (UUID. 0 1)
                    :name "Congregation 1"}
                   {:id (UUID. 0 2)
                    :name "Congregation 2"}]
   :logged-in? true
   :demo-available? true})
(def anonymous-model
  {:congregations []
   :logged-in? false
   :demo-available? true})
(def no-demo-model
  {:congregations []
   :logged-in? false
   :demo-available? false})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id1 (at/create-congregation! session "Congregation 1")
          cong-id2 (at/create-congregation! session "Congregation 2")]

      (binding [config/env (replace-in config/env [:demo-congregation] nil (UUID. 0 42))]
        (testing "logged in, with congregations"
          (is (= (-> model
                     (replace-in [:congregations 0 :id] (UUID. 0 1) cong-id1)
                     (replace-in [:congregations 1 :id] (UUID. 0 2) cong-id2))
                 (home-page/model! {:session {::auth/user {:user/id user-id}}}))))

        (testing "anonymous user"
          (is (= anonymous-model
                 (home-page/model! {})))))

      (testing "anonymous user, no demo"
        (is (= no-demo-model
               (home-page/model! {})))))))

(deftest view-test
  (let [introduction "Territory Bro
                      Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.
                      See territorybro.com for more information. "]
    (testing "logged in, some congregations"
      (is (= (html/normalize-whitespace
              (str introduction
                   "Your congregations
                      Congregation 1
                      Congregation 2

                    View a demo
                    Register a new congregation
                    Join an existing congregation"))
             (-> (home-page/view model)
                 html/visible-text))))

    (testing "logged in, zero congregations"
      (is (= (html/normalize-whitespace
              (str introduction
                   "View a demo
                    Register a new congregation
                    Join an existing congregation"))
             (-> (home-page/view (dissoc model :congregations))
                 html/visible-text))))

    (testing "logged in, zero congregations, no demo"
      (is (= (html/normalize-whitespace
              (str introduction
                   "Register a new congregation
                    Join an existing congregation"))
             (-> (home-page/view (-> (dissoc model :congregations)
                                     (replace-in [:demo-available?] true false)))
                 html/visible-text))))

    (testing "anonymous user"
      (is (= (html/normalize-whitespace
              (str introduction
                   "Login
                    View a demo
                    Register a new congregation
                    Join an existing congregation"))
             (-> (home-page/view anonymous-model)
                 html/visible-text))))

    (testing "anonymous user, no demo"
      (is (= (html/normalize-whitespace
              (str introduction
                   "Login
                    Register a new congregation
                    Join an existing congregation"))
             (-> (home-page/view no-demo-model)
                 html/visible-text))))))
