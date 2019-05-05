;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.routes-test
  (:require [clojure.test :refer :all]
            [territory-bro.authentication :as auth]
            [territory-bro.config :refer [env]]
            [territory-bro.routes :refer :all]))

(deftest find-tenant-test
  (let [tenants [:foo :bar]]
    (testing "valid tenant"
      (is (= :foo (find-tenant {:headers {"x-tenant" "foo"}} tenants)))
      (is (= :bar (find-tenant {:headers {"x-tenant" "bar"}} tenants))))
    (testing "invalid tenant"
      (is (= nil (find-tenant {:headers {"x-tenant" "gazonk"}} tenants))))
    (testing "unspecified tenant"
      (is (= nil (find-tenant {:headers {}} tenants))))))

(deftest dev-login-test
  (testing "authenticates as anybody in dev mode"
    (binding [env {:dev true}
              save-user-from-jwt! (fn [_])]
      (is (= {:status 200,
              :headers {},
              :body "Logged in",
              :session {::auth/user {:sub "sub",
                                     :name "name",
                                     :email "email"}}}
             (dev-login {:params {:sub "sub"
                                  :name "name"
                                  :email "email"}})))))

  (testing "is disabled when not in dev mode"
    (binding [env {:dev false}]
      (is (= {:status 403
              :headers {}
              :body "Dev mode disabled"}
             (dev-login {:params {:sub "sub"
                                  :name "name"
                                  :email "email"}}))))))

(deftest api-format-test
  (is (= {} (format-for-api {})))
  (is (= {"foo" 1} (format-for-api {:foo 1})))
  (is (= {"fooBar" 1} (format-for-api {:foo-bar 1})))
  (is (= {"bar" 1} (format-for-api {:foo/bar 1})))
  (is (= [{"foo" 1} {"bar" 2}] (format-for-api [{:foo 1} {:bar 2}]))))
