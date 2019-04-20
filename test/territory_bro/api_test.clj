;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.api-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [territory-bro.config :as config]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.router :refer [app]]
            [territory-bro.testing :refer [api-fixture transaction-rollback-fixture]])
  (:import (java.time Instant)))

(use-fixtures :once api-fixture)
(use-fixtures :each transaction-rollback-fixture)

(defn- get-cookies [response]
  (->> (get-in response [:headers "Set-Cookie"])
       (map (fn [header]
              (let [[name value] (-> (first (str/split header #";" 2))
                                     (str/split #"=" 2))]
                [name {:value value}])))
       (into {})))

(deftest test-get-cookies
  (is (= {} (get-cookies {})))
  (is (= {"ring-session" {:value "123"}}
         (get-cookies {:headers {"Set-Cookie" ["ring-session=123"]}})))
  (is (= {"ring-session" {:value "123"}}
         (get-cookies {:headers {"Set-Cookie" ["ring-session=123;Path=/;HttpOnly;SameSite=Strict"]}})))
  (is (= {"foo" {:value "123"}
          "bar" {:value "456"}}
         (get-cookies {:headers {"Set-Cookie" ["foo=123" "bar=456"]}}))))

(defn assert-response-status [response expected-status]
  (assert (= expected-status (:status response))
          {:response response})
  response)

(defn login! [app]
  (let [response (-> (request :post "/api/login")
                     (json-body {:idToken jwt-test/token})
                     app
                     (assert-response-status 200))]
    {:cookies (get-cookies response)}))

(defn logout! [app session]
  (-> (request :post "/api/logout")
      (merge session)
      app
      (assert-response-status 200)))


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
      (is (= "Logged in" (:body response)))
      (is (= ["ring-session"] (keys (get-cookies response))))))

  (testing "login with expired token"
    (binding [config/env (assoc config/env :now #(Instant/now))]
      (let [response (-> (request :post "/api/login")
                         (json-body {:idToken jwt-test/token})
                         app)]
        (is (= 403 (:status response)))
        (is (= "Invalid token" (:body response)))
        (is (empty? (get-cookies response))))))

  (testing "dev login"
    (binding [config/env (assoc config/env :dev true)]
      (let [response (-> (request :post "/api/dev-login")
                         (json-body {:sub "developer"
                                     :name "Developer"
                                     :email "developer@example.com"})
                         app)]
        (is (= 200 (:status response)))
        (is (= "Logged in" (:body response)))
        (is (= ["ring-session"] (keys (get-cookies response)))))))

  (testing "dev login outside dev mode"
    (let [response (-> (request :post "/api/dev-login")
                       (json-body {:sub "developer"
                                   :name "Developer"
                                   :email "developer@example.com"})
                       app)]
      (is (= 404 (:status response)))
      (is (= "Dev mode disabled" (:body response)))
      (is (empty? (get-cookies response))))))

(deftest test-authorization
  (testing "before login"
    (let [response (-> (request :get "/api/my-congregations")
                       app)]
      (is (= 401 (:status response)))
      (is (= "Not logged in" (:body response)))))

  (let [session (login! app)]
    (testing "after login"
      (let [response (-> (request :get "/api/my-congregations")
                         (merge session)
                         app)]
        (is (= 200 (:status response)))))

    (testing "after logout"
      (logout! app session)
      (let [response (-> (request :get "/api/my-congregations")
                         (merge session)
                         app)]
        (is (= 401 (:status response)))
        (is (= "Not logged in" (:body response)))))))
