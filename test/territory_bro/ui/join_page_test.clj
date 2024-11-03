(ns territory-bro.ui.join-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.join-page :as join-page])
  (:import (clojure.lang ExceptionInfo)
           (java.util UUID)))

(def user-id (UUID. 0 1))
(def model {:user-id (UUID. 0 1)})

(deftest model!-test
  (let [request {}]

    (testing "logged in"
      (testutil/with-user-id user-id
        (is (= model (join-page/model! request)))))

    (testing "anonymous"
      (testutil/with-anonymous-user
        (is (thrown-match? ExceptionInfo dmz-test/not-logged-in
                           (join-page/model! request)))))))

(deftest view-test
  (is (= (html/normalize-whitespace
          "Join an existing congregation

           To access a congregation in Territory Bro, you will need to tell your User ID
           to your congregation's territory servant, so that he can give you access.

           Your User ID is:
           00000000-0000-0000-0000-000000000001
           Copy to clipboard")
         (-> (join-page/view model)
             html/visible-text))))
