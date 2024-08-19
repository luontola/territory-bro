;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.home-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.home-page :as home-page]
            [territory-bro.ui.html :as html])
  (:import (java.util UUID)))

(def user-id (UUID. 1 0))
(def cong-id1 (UUID. 0 1))
(def cong-id2 (UUID. 0 2))
(def model
  {:congregations [{:congregation/id cong-id1
                    :congregation/name "Congregation 1"}
                   {:congregation/id cong-id2
                    :congregation/name "Congregation 2"}]
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

(deftest model!-test
  (let [request {}]
    (testutil/with-events (flatten [{:event/type :congregation.event/congregation-created
                                     :congregation/id cong-id1
                                     :congregation/name "Congregation 1"
                                     :congregation/schema-name "cong1_schema"}
                                    (congregation/admin-permissions-granted cong-id1 user-id)

                                    {:event/type :congregation.event/congregation-created
                                     :congregation/id cong-id2
                                     :congregation/name "Congregation 2"
                                     :congregation/schema-name "cong2_schema"}
                                    (congregation/admin-permissions-granted cong-id2 user-id)

                                    {:event/type :congregation.event/congregation-created
                                     :congregation/id (UUID. 0 0x666)
                                     :congregation/name "Unrelated Congregation"
                                     :congregation/schema-name "cong3_schema"}])
      (binding [config/env {:demo-congregation (UUID/randomUUID)}]
        (testing "logged in, with congregations"
          (testutil/with-user-id user-id
            (is (= (-> model
                       (replace-in [:congregations 0 :congregation/id] (UUID. 0 1) cong-id1)
                       (replace-in [:congregations 1 :congregation/id] (UUID. 0 2) cong-id2))
                   (home-page/model! request)))))

        (testing "anonymous"
          (testutil/with-anonymous-user
            (is (= anonymous-model (home-page/model! request))))))

      (binding [config/env {:demo-congregation nil}]
        (testing "anonymous, no demo"
          (testutil/with-anonymous-user
            (is (= no-demo-model (home-page/model! request)))))))))

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

    (testing "anonymous"
      (is (= (html/normalize-whitespace
              (str introduction
                   "Login
                    View a demo
                    Register a new congregation
                    Join an existing congregation"))
             (-> (home-page/view anonymous-model)
                 html/visible-text))))

    (testing "anonymous, no demo"
      (is (= (html/normalize-whitespace
              (str introduction
                   "Login
                    Register a new congregation
                    Join an existing congregation"))
             (-> (home-page/view no-demo-model)
                 html/visible-text))))))
