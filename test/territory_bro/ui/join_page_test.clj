;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.join-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
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

    (testing "anonymous user"
      (testutil/with-anonymous-user
        (is (thrown-match? ExceptionInfo
                           {:type :ring.util.http-response/response
                            :response {:status 401
                                       :body "Not logged in"
                                       :headers {}}}
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
