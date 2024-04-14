;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.support-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.support-page :as support-page]))

(def model
  {:support-email "support@example.com"})
(def anonymous-model
  {:support-email nil})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          request {:session (auth/user-session {:name "John Doe"} user-id)}]

      (testing "logged in"
        (is (= model (support-page/model! request))))

      (testing "anonymous user"
        (is (= anonymous-model (support-page/model! (dissoc request :session)))))

      (testing "no support email configured"
        (binding [config/env (replace-in config/env [:support-email] "support@example.com" nil)]
          (is (= anonymous-model (support-page/model! request))))))))

(deftest view-test
  (testing "default"
    (is (= (html/normalize-whitespace
            "Support
             Territory Bro is an open source project developed by Esko Luontola.
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.
             The user guide should answer the most common questions related to creating territory maps.
             If that is not enough, you may email support@example.com to ask for help with using Territory Bro.
             See the translation instructions if you would like to help improve the current translations or add new languages.
             Bugs and feature requests may also be reported to this project's issue tracker.")
           (-> (support-page/view model)
               html/visible-text))))

  (testing "anonymous user"
    (is (= (html/normalize-whitespace
            "Support
             Territory Bro is an open source project developed by Esko Luontola.
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.
             The user guide should answer the most common questions related to creating territory maps.
             See the translation instructions if you would like to help improve the current translations or add new languages.
             Bugs and feature requests may also be reported to this project's issue tracker.")
           (-> (support-page/view anonymous-model)
               html/visible-text)))))
