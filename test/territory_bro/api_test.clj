;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.api-test
  (:require [cheshire.core :refer [parse-stream]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [ring.util.http-predicates :refer :all]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture api-fixture]]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.jwt :as jwt]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.router :as router]
            [territory-bro.user :as user])
  (:import (java.time Instant)
           (java.util UUID)))

(use-fixtures :once (join-fixtures [db-fixture api-fixture]))

(defn- get-cookies [response]
  (->> (get-in response [:headers "Set-Cookie"])
       (map (fn [header]
              (let [[name value] (-> (first (str/split header #";" 2))
                                     (str/split #"=" 2))]
                [name {:value value}])))
       (into {})))

(deftest get-cookies-test
  (is (= {} (get-cookies {})))
  (is (= {"ring-session" {:value "123"}}
         (get-cookies {:headers {"Set-Cookie" ["ring-session=123"]}})))
  (is (= {"ring-session" {:value "123"}}
         (get-cookies {:headers {"Set-Cookie" ["ring-session=123;Path=/;HttpOnly;SameSite=Strict"]}})))
  (is (= {"foo" {:value "123"}
          "bar" {:value "456"}}
         (get-cookies {:headers {"Set-Cookie" ["foo=123" "bar=456"]}}))))

(defn parse-json [body]
  (cond
    (nil? body) body
    (string? body) body
    :else (parse-stream (io/reader body) true)))

(defn read-body [response]
  (update response :body parse-json))

(defn app [request]
  (-> request router/app read-body))

(defn assert-response [response predicate]
  (assert (predicate response)
          (str "Unexpected response " response))
  response)

(defn login! [app]
  (let [response (-> (request :post "/api/login")
                     (json-body {:idToken jwt-test/token})
                     app
                     (assert-response ok?))]
    {:cookies (get-cookies response)}))

(defn logout! [app session]
  (-> (request :post "/api/logout")
      (merge session)
      app
      (assert-response ok?)))


(deftest basic-routes-test
  (testing "index"
    (let [response (-> (request :get "/")
                       app)]
      (is (ok? response))))

  (testing "page not found"
    (let [response (-> (request :get "/invalid")
                       app)]
      (is (not-found? response)))))

(deftest login-test
  (testing "login with valid token"
    (let [response (-> (request :post "/api/login")
                       (json-body {:idToken jwt-test/token})
                       app)]
      (is (ok? response))
      (is (= "Logged in" (:body response)))
      (is (= ["ring-session"] (keys (get-cookies response))))))

  (testing "user is saved on login"
    (db/with-db [conn {}]
      (let [user (user/get-by-subject conn (:sub (jwt/validate jwt-test/token config/env)))]
        (is user)
        (is (= "Esko Luontola" (get-in user [::user/attributes :name]))))))

  (testing "login with expired token"
    (binding [config/env (assoc config/env :now #(Instant/now))]
      (let [response (-> (request :post "/api/login")
                         (json-body {:idToken jwt-test/token})
                         app)]
        (is (forbidden? response))
        (is (= "Invalid token" (:body response)))
        (is (empty? (get-cookies response))))))

  (testing "dev login"
    (binding [config/env (assoc config/env :dev true)]
      (let [response (-> (request :post "/api/dev-login")
                         (json-body {:sub "developer"
                                     :name "Developer"
                                     :email "developer@example.com"})
                         app)]
        (is (ok? response))
        (is (= "Logged in" (:body response)))
        (is (= ["ring-session"] (keys (get-cookies response)))))))

  (testing "user is saved on dev login"
    (db/with-db [conn {}]
      (let [user (user/get-by-subject conn "developer")]
        (is user)
        (is (= "Developer" (get-in user [::user/attributes :name]))))))

  (testing "dev login outside dev mode"
    (let [response (-> (request :post "/api/dev-login")
                       (json-body {:sub "developer"
                                   :name "Developer"
                                   :email "developer@example.com"})
                       app)]
      (is (forbidden? response))
      (is (= "Dev mode disabled" (:body response)))
      (is (empty? (get-cookies response))))))

(deftest authorization-test
  (testing "before login"
    (let [response (-> (request :get "/api/my-congregations")
                       app)]
      (is (unauthorized? response))))

  (let [session (login! app)]
    (testing "after login"
      (let [response (-> (request :get "/api/my-congregations")
                         (merge session)
                         app)]
        (is (ok? response))))

    (testing "after logout"
      (logout! app session)
      (let [response (-> (request :get "/api/my-congregations")
                         (merge session)
                         app)]
        (is (unauthorized? response))))))

(deftest create-congregation-test
  (let [session (login! app)
        response (-> (request :post "/api/congregations")
                     (json-body {:name "foo"})
                     (merge session)
                     app)]
    (is (ok? response))
    (is (:id (:body response)))

    (let [cong-id (UUID/fromString (:id (:body response)))]
      (db/with-db [conn {}]
        (testing "grants access to the current user"
          (is (= 1 (count (congregation/get-users conn cong-id)))))

        (testing "creates a GIS user for the current user"
          (is (= 1 (count (gis-user/get-gis-users conn {:congregation cong-id}))))))))

  (testing "requires login"
    (let [response (-> (request :post "/api/congregations")
                       (json-body {:name "foo"})
                       app)]
      (is (unauthorized? response)))))

(deftest list-congregations-test
  (let [session (login! app)
        response (-> (request :get "/api/congregations")
                     (merge session)
                     app)]
    (is (ok? response))
    (is (sequential? (:body response))))

  (testing "requires login"
    (let [response (-> (request :get "/api/congregations")
                       app)]
      (is (unauthorized? response)))))
