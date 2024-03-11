;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.api :as api]
            [territory-bro.api-test :as at]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-list-page :as territory-list-page])
  (:import (java.util UUID)))

(def model
  {:territories [{:id (UUID. 0 1)
                  :number "123"
                  :addresses "the addresses"
                  :region "the region"
                  :meta {:foo "bar"}
                  :location testdata/wkt-multi-polygon}]
   :permissions {:configureCongregation true
                 :editDoNotCalls true
                 :gisAccess true
                 :shareTerritoryLink true
                 :viewCongregation true}})
(def anonymous-model
  (assoc model :permissions {}))

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "foo")
          territory-id (at/create-territory! cong-id)
          request {:params {:congregation (str cong-id)
                            :territory (str territory-id)}
                   :session {::auth/user {:user/id user-id}}}]
      (testing "default"
        (is (= (-> model
                   (replace-in [:territories 0 :id] (UUID. 0 1) territory-id))
               (territory-list-page/model! request))))

      (testing "anonymous user, has opened a share"
        (at/create-share! cong-id territory-id "share123")
        (let [{:keys [session]} (api/open-share {:params {:share-key "share123"}})
              request (assoc request :session session)]
          (is (= (-> anonymous-model
                     (replace-in [:territories 0 :id] (UUID. 0 1) territory-id))
                 (territory-list-page/model! request))))))))

(deftest view-test
  (testing "default"
    (is (= (html/normalize-whitespace
            "Territories

             Number   Region       Addresses
             123      the region   the addresses")
           (-> (territory-list-page/view model)
               html/visible-text))))

  (testing "anonymous user, has opened a share"
    (is (= (html/normalize-whitespace
            "Territories

             {fa-info-circle} Why so few territories?
             Only those territories which have been shared with you are currently shown.
             You will need to login to see the rest.

             Number   Region       Addresses
             123      the region   the addresses")
           (-> (territory-list-page/view anonymous-model)
               html/visible-text))))

  (testing "logged-in user without congregation access, has opened a share"
    (is (= (html/normalize-whitespace
            "Territories

             {fa-info-circle} Why so few territories?
             Only those territories which have been shared with you are currently shown.
             You will need to request access to see the rest.

             Number   Region       Addresses
             123      the region   the addresses")
           (binding [auth/*user* {:user/id (UUID/randomUUID)}]
             (-> (territory-list-page/view anonymous-model)
                 html/visible-text))))))
