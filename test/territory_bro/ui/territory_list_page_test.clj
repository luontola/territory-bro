;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page-test
  (:require [clojure.test :refer :all]
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
                  :location testdata/wkt-multi-polygon}]})

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
      (is (= (-> model
                 (replace-in [:territories 0 :id] (UUID. 0 1) territory-id))
             (territory-list-page/model! request))))))

(deftest view-test
  (is (= (html/normalize-whitespace
          "Territories
           Number   Region       Addresses
           123      the region   the addresses")
         (-> (territory-list-page/view model)
             html/visible-text))))
