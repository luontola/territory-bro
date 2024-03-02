;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-page :as territory-page])
  (:import (java.util UUID)))

(def base-model
  {:territory {:id (UUID. 0 1)
               :number "123"
               :addresses "the addresses"
               :region "the region"
               :meta {:foo "bar"}
               :location testdata/wkt-multi-polygon
               :doNotCalls "the do-not-calls"}})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    #_(let [state (ft/apply-events [ft/congregation-created
                                    ft/territory-defined])])
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "foo")
          territory-id (at/create-territory! cong-id)
          request {:params {:congregation (str cong-id)
                            :territory (str territory-id)}
                   :session {::auth/user {:user/id user-id}}}]
      (is (= (assoc-in base-model [:territory :id] territory-id)
             (territory-page/model! request))))))

(deftest view-test
  (is (= (html/normalize-whitespace
          "Territory 123

           Number
             123
           Region
             the region
           Addresses
             the addresses
           Do not contact
             Edit
             the do-not-calls

           {fa-share-nodes} Share a link")
         (-> (territory-page/view base-model)
             html/visible-text))))


;;;; Components and helpers

(deftest do-not-calls-test
  (let [model {:territory {:doNotCalls "the do-not-calls"}}]
    (testing "viewing"
      (is (= (html/normalize-whitespace
              "Edit
               the do-not-calls")
             (-> (territory-page/do-not-calls--viewing model)
                 html/visible-text))))

    (testing "editing"
      (is (= (html/normalize-whitespace
              "the do-not-calls
               Save")
             (-> (territory-page/do-not-calls--editing model)
                 html/visible-text))))))

(deftest share-link-test
  (testing "closed"
    (is (= "{fa-share-nodes} Share a link"
           (-> (territory-page/share-link {:open? false})
               html/visible-text))))

  (testing "open"
    (is (= (html/normalize-whitespace
            "{fa-share-nodes} Share a link
             {fa-xmark}
             People with this link will be able to view this territory map without logging in:
             https://territorybro.com/link {fa-copy}")
           (-> (territory-page/share-link {:open? true
                                           :link "https://territorybro.com/link"})
               html/visible-text)))))
