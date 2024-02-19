;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.auth0-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [ring.util.codec :as codec]
            [ring.util.http-predicates :as http-predicates]
            [ring.util.response :as response]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.config :as config])
  (:import (java.net URL)
           (javax.servlet.http HttpServletRequest HttpServletResponse)))

(defn config-fixture [f]
  (mount/start #'config/env
               #'auth0/auth-controller)
  (f)
  (mount/stop))

(use-fixtures :once config-fixture)

(deftest login-handler-test
  (let [request {:request-method :get
                 :uri "/login"
                 :headers {}}
        response (auth0/login-handler request)
        location (URL. (response/get-header response "Location"))
        query-params (-> (codec/form-decode (.getQuery location))
                         (update-keys keyword))]

    (testing "redirects to Auth0"
      (is (http-predicates/see-other? response))
      (is (= "luontola.eu.auth0.com" (.getHost location)))
      (is (= {:redirect_uri "http://localhost:8081/login-callback"
              :client_id "8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi"
              :scope "openid"
              :response_type "code"}
             (dissoc query-params :state))))

    (testing "saves OIDC state in session"
      (let [state (:state query-params)]
        (is (not (str/blank? state)))
        (is (= [(str "com.auth0.state=" state "; HttpOnly; Max-Age=600; SameSite=Lax")]
               (response/get-header response "Set-Cookie")))
        (is (= {:territory-bro.infra.auth0/servlet {"com.auth0.state" state
                                                    "com.auth0.nonce" nil}} ; nonce is not needed in authorization code flow, see https://community.auth0.com/t/is-nonce-requried-for-the-authoziation-code-flow/111419
               (:session response)))))))

(deftest ring->servlet-test
  (testing "response headers"
    (let [[_ ^HttpServletResponse response *ring-response] (auth0/ring->servlet {})]
      (.addHeader response "foo" "bar")
      (is (= "bar" (.getHeader response "foo")))
      (is (= {"foo" "bar"} (:headers @*ring-response)))))

  (testing "sessions:"
    (testing "getSession(create=false) when session doesn't exist"
      (let [[^HttpServletRequest request _ *ring-response] (auth0/ring->servlet {})]
        (is (nil? (.getSession request false)))
        (is (= {}
               (select-keys @*ring-response [:session])))))

    (testing "getSession(create=false) when session exists"
      (let [[^HttpServletRequest request _ *ring-response] (auth0/ring->servlet {:session {}})]
        (is (some? (.getSession request false)))
        (is (= {:session {}}
               (select-keys @*ring-response [:session])))))

    (testing "getSession(create=true) when session doesn't exist"
      (let [[^HttpServletRequest request _ *ring-response] (auth0/ring->servlet {})]
        (is (some? (.getSession request true)))
        (is (= {:session {}}
               (select-keys @*ring-response [:session])))))

    (testing "getSession(create=true) when session exists"
      (let [[^HttpServletRequest request _ *ring-response] (auth0/ring->servlet {:session {}})]
        (is (some? (.getSession request true)))
        (is (= {:session {}}
               (select-keys @*ring-response [:session]))))))

  (testing "session attributes"
    (let [[^HttpServletRequest request _ *ring-response] (auth0/ring->servlet {})
          session (.getSession request true)]
      (is (nil? (.getAttribute session "foo")))
      (.setAttribute session "foo" "bar")
      (is (nil? (.getAttribute session "bar")))
      (is (= {:session {::auth0/servlet {"foo" "bar"}}}
             (select-keys @*ring-response [:session]))))))
