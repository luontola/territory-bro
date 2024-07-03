;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printouts-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.printouts-page :as printouts-page])
  (:import (java.util UUID)))

(def model
  {:congregation {:id (UUID. 0 1)
                  :name "Example Congregation"
                  :locations [testdata/wkt-multi-polygon]}
   :regions [{:id (UUID. 0 2)
              :name "the region"
              :location testdata/wkt-multi-polygon}]
   :territories [{:id (UUID. 0 3)
                  :number "123"
                  :addresses "the addresses"
                  :region "the region"
                  :meta {:foo "bar"}
                  :location testdata/wkt-multi-polygon}]
   :mac? false})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "Example Congregation")
          congregation-boundary-id (at/create-congregation-boundary! cong-id)
          region-id (at/create-region! cong-id)
          territory-id (at/create-territory! cong-id)
          request {:params {:congregation (str cong-id)}
                   :session {::auth/user {:user/id user-id}}}
          fix (fn [model]
                (-> model
                    (replace-in [:congregation :id] (UUID. 0 1) cong-id)
                    (replace-in [:regions 0 :id] (UUID. 0 2) region-id)
                    (replace-in [:territories 0 :id] (UUID. 0 3) territory-id)))]

      (testing "default"
        (is (= (fix model)
               (printouts-page/model! request)))))))
