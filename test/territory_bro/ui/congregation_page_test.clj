;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.congregation-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.infra.config :as config]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.ui.congregation-page :as congregation-page]
            [territory-bro.ui.html :as html])
  (:import (java.util UUID)))

(def model
  {:congregation {:congregation/name "Example Congregation"}
   :permissions {:view-printouts-page true
                 :view-settings-page true}})
(def demo-model
  {:congregation {:congregation/name "Demo Congregation"}
   :permissions {:view-printouts-page true
                 :view-settings-page false}})

(deftest model!-test
  (let [user-id (UUID/randomUUID)
        cong-id (UUID/randomUUID)
        request {:path-params {:congregation cong-id}}]
    (binding [config/env {:demo-congregation cong-id}]
      (testutil/with-events (flatten [{:event/type :congregation.event/congregation-created
                                       :congregation/id cong-id
                                       :congregation/name "Example Congregation"
                                       :congregation/schema-name "cong1_schema"}
                                      (congregation/admin-permissions-granted cong-id user-id)])
        (testutil/with-user-id user-id

          (testing "default"
            (is (= model (congregation-page/model! request))))

          (testing "demo congregation"
            (let [request {:path-params {:congregation "demo"}}]
              (is (= demo-model (congregation-page/model! request))))))))))

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
