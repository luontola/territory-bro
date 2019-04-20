;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.api-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [territory-bro.config :as config]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.router :refer [app]]
            [territory-bro.testing :refer [api-fixture transaction-rollback-fixture]])
  (:import (java.time Instant)))

(use-fixtures :once api-fixture)
(use-fixtures :each transaction-rollback-fixture)

(deftest test-basic-routes
  (testing "index"
    (let [response (-> (request :get "/")
                       app)]
      (is (= 200 (:status response)))))

  (testing "page not found"
    (let [response (-> (request :get "/invalid")
                       app)]
      (is (= 404 (:status response))))))

(deftest test-login
  (testing "login with valid token"
    (let [response (-> (request :post "/api/login")
                       (json-body {:idToken jwt-test/token})
                       app)]
      (is (= 200 (:status response)))
      (is (= "Logged in" (:body response)))))

  (testing "login with expired token"
    (binding [config/env (assoc config/env :now #(Instant/now))]
      (let [response (-> (request :post "/api/login")
                         (json-body {:idToken jwt-test/token})
                         app)]
        (is (= 403 (:status response)))
        (is (= "Invalid token" (:body response))))))

  (testing "dev login"
    (binding [config/env (assoc config/env :dev true)]
      (let [response (-> (request :post "/api/dev-login")
                         (json-body {:sub "developer"
                                     :name "Developer"
                                     :email "developer@example.com"})
                         app)]
        (is (= 200 (:status response)))
        (is (= "Logged in" (:body response))))))

  (testing "dev login outside dev mode"
    (let [response (-> (request :post "/api/dev-login")
                       (json-body {:sub "developer"
                                   :name "Developer"
                                   :email "developer@example.com"})
                       app)]
      (is (= 404 (:status response)))
      (is (= "Dev mode disabled" (:body response))))))
