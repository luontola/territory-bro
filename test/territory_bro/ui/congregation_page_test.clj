;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.congregation-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui :as ui]
            [territory-bro.ui.congregation-page :as congregation-page]
            [territory-bro.ui.html :as html]))

(def model
  {:congregation/name "Example Congregation"
   :congregation/permissions {:configure-congregation true
                              :edit-do-not-calls true
                              :gis-access true
                              :share-territory-link true
                              :view-congregation true}})
(def demo-model
  {:congregation/name "Demo Congregation"
   :congregation/permissions {:share-territory-link true
                              :view-congregation true}})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "Example Congregation")
          request {:path-params {:congregation cong-id}}]
      (auth/with-user-id user-id

        (testing "regular congregation"
          (is (= model ((ui/wrap-current-state congregation-page/model!) request))))

        (testing "demo congregation"
          (binding [config/env (replace-in config/env [:demo-congregation] nil cong-id)]
            (let [request {:path-params {:congregation "demo"}}]
              (is (= demo-model
                     ((ui/wrap-current-state congregation-page/model!) request)
                     (auth/with-anonymous-user
                       ((ui/wrap-current-state congregation-page/model!) request)))))))))))

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
           (-> (congregation-page/view (dissoc model :congregation/permissions))
               html/visible-text)))))
