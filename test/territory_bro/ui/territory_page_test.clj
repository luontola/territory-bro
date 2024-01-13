;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.ui.territory-page-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer [request]]
            [territory-bro.api-test :as at]
            [territory-bro.domain.facade-test :as ft]
            [territory-bro.test.fixtures :refer [api-fixture db-fixture]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-page :as territory-page]))

;; TODO: decouple UI tests from the database
(use-fixtures :once (join-fixtures [db-fixture api-fixture]))

(deftest page-test
  (let [session (at/login! at/app)
        cong-id (at/create-congregation! session "foo")
        territory-id (at/create-territory! cong-id)
        territory (-> (request :get (str "/api/congregation/" cong-id "/territory/" territory-id))
                      (merge session)
                      at/app
                      :body)]
    #_(let [state (ft/apply-events [ft/congregation-created
                                    ft/territory-defined])])
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

             Share a link")
           (-> (territory-page/page territory)
               html/visible-text)))))


(deftest do-not-calls-test
  (testing "viewing"
    (is (= (html/normalize-whitespace
            "Edit
             the do-not-calls")
           (-> (territory-page/do-not-calls--view {:doNotCalls "the do-not-calls"})
               html/visible-text))))

  (testing "editing"
    (is (= (html/normalize-whitespace
            "the do-not-calls
             Save")
           (-> (territory-page/do-not-calls--edit {:doNotCalls "the do-not-calls"})
               html/visible-text)))))

(deftest share-link-test
  (testing "closed"
    (is (= "Share a link"
           (-> (territory-page/share-link {:open? false})
               html/visible-text))))

  (testing "open"
    (is (= (html/normalize-whitespace
            "Share a link
            {faXmark}
            People with this link will be able to view this territory map without logging in:
            https://territorybro.com/link
            {faCopy}")
           (-> (territory-page/share-link {:open? true
                                           :link "https://territorybro.com/link"})
               html/visible-text)))))
