(ns territory-bro.ui.home-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil]
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
                    :congregation/name "Congregation 2"}]})
(def empty-model
  {:congregations []})

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
      (testing "logged in, with congregations"
        (testutil/with-user-id user-id
          (is (= model (home-page/model! request)))))

      (testing "anonymous or no congregations"
        (testutil/with-anonymous-user
          (is (= empty-model (home-page/model! request))))))))

(deftest my-congregations-sidebar-test
  (testing "logged in, with congregations"
    (is (= (html/normalize-whitespace
            "Your congregations
               Congregation 1
               Congregation 2")
           (-> (home-page/my-congregations-sidebar model)
               html/visible-text))))

  (testing "anonymous or no congregations"
    (is (= ""
           (-> (home-page/my-congregations-sidebar empty-model)
               html/visible-text)))))

(deftest view-test
  (testing "renders markdown content"
    (is (str/includes? (home-page/view empty-model)
                       "<p>Territory Bro is a web app for managing field service territories in the congregations of Jehovah's Witnesses.</p>"))))
