;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.congregation-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.ui.congregation-page :as congregation-page]
            [territory-bro.ui.html :as html]))

(def model
  {:name "Example Congregation"
   :permissions {:configureCongregation true
                 :editDoNotCalls true
                 :gisAccess true
                 :shareTerritoryLink true
                 :viewCongregation true}})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "Example Congregation")
          request {:params {:congregation (str cong-id)}
                   :session {::auth/user {:user/id user-id}}}]
      (is (= model
             (congregation-page/model! request))))))

(deftest view-test
  (testing "full permissions"
    (is (= (html/normalize-whitespace
            "Example Congregation
             Territories
             Printouts
             Settings")
           (-> (congregation-page/view model)
               html/visible-text))))

  (testing "minimal permissions"
    (is (= (html/normalize-whitespace
            "Example Congregation
             Territories")
           (-> (congregation-page/view (dissoc model :permissions))
               html/visible-text)))))
