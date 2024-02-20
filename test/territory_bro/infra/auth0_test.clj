;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.auth0-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [ring.middleware.cookies :as cookies]
            [ring.util.codec :as codec]
            [ring.util.http-predicates :as http-predicates]
            [ring.util.response :as response]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.config :as config])
  (:import (java.net URL)
           (javax.servlet.http Cookie HttpServletRequest HttpServletResponse)))

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
  (testing "request URL"
    ;; Servlet spec: The returned URL contains a protocol, server name, port number,
    ;; and server path, but it does not include query string parameters.
    (testing "http, custom port"
      (let [[^HttpServletRequest request _ _] (auth0/ring->servlet {:scheme :http
                                                                    :server-name "localhost"
                                                                    :server-port 8081
                                                                    :uri "/the/path"})]
        (is (= "http://localhost:8081/the/path" (str (.getRequestURL request))))))

    (testing "https, default port"
      (let [[^HttpServletRequest request _ _] (auth0/ring->servlet {:scheme :https
                                                                    :server-name "example.com"
                                                                    :server-port 443
                                                                    :uri "/"})]
        (is (= "https://example.com/" (str (.getRequestURL request)))))))

  (testing "request parameters"
    (let [[^HttpServletRequest request _ _] (auth0/ring->servlet {:params {:foo "bar"}})]
      (testing "no value"
        (is (nil? (.getParameter request "nope"))))

      (testing "some value"
        (is (= "bar" (.getParameter request "foo"))))))

  (testing "request cookies"
    (let [[^HttpServletRequest request _ _] (auth0/ring->servlet {})]
      (testing "no cookies"
        (is (nil? (.getCookies request)))))

    (let [[^HttpServletRequest request _ _] (auth0/ring->servlet {:cookies {"foo" {:value "bar"}}})]
      (testing "some cookies"
        (is (= [["foo" "bar"]]
               (->> (.getCookies request)
                    (map (juxt #(.getName %) #(.getValue %)))))))))

  (testing "response cookies"
    (let [[_ ^HttpServletResponse response *ring-response] (auth0/ring->servlet {})]
      (testing "set cookie"
        (.addCookie response (Cookie. "foo" "bar"))
        (is (= {"foo" {:value "bar"}}
               (:cookies @*ring-response))))

      (testing "delete cookie"
        (.addCookie response (doto (Cookie. "foo" "")
                               (.setMaxAge 0)))
        (is (= {"Set-Cookie" ["foo=; Max-Age=0"]}
               (:headers (cookies/cookies-response @*ring-response)))))

      (testing "set cookie with all options (supported by both Servlet and Ring APIs)"
        (.addCookie response (doto (Cookie. "foo" "bar")
                               (.setPath "/the/path")
                               (.setDomain "example.com")
                               (.setMaxAge 3600)
                               (.setSecure true)
                               (.setHttpOnly true)))
        (is (= {"Set-Cookie" ["foo=bar; Path=/the/path; Domain=example.com; Max-Age=3600; Secure; HttpOnly"]}
               (:headers (cookies/cookies-response @*ring-response)))))

      (testing "max age = -1"
        ;; Servlet spec: A negative value means that the cookie is not stored persistently and will be deleted when the Web browser exits.
        ;; HTTP spec: A zero or negative number will expire the cookie immediately.
        (.addCookie response (doto (Cookie. "foo" "bar")
                               (.setMaxAge -1)))
        (is (= {"Set-Cookie" ["foo=bar"]}
               (:headers (cookies/cookies-response @*ring-response)))))

      (testing "max age = 0"
        ;; Servlet spec: A zero value causes the cookie to be deleted.
        ;; HTTP spec: A zero or negative number will expire the cookie immediately.
        (.addCookie response (doto (Cookie. "foo" "bar")
                               (.setMaxAge 0)))
        (is (= {"Set-Cookie" ["foo=bar; Max-Age=0"]}
               (:headers (cookies/cookies-response @*ring-response)))))

      (testing "max age = 1"
        (.addCookie response (doto (Cookie. "foo" "bar")
                               (.setMaxAge 1)))
        (is (= {"Set-Cookie" ["foo=bar; Max-Age=1"]}
               (:headers (cookies/cookies-response @*ring-response)))))))

  (testing "response headers"
    (let [[_ ^HttpServletResponse response *ring-response] (auth0/ring->servlet {})]
      (testing "no value"
        (is (nil? (.getHeader response "foo")))
        (is (= [] (.getHeaders response "foo")))
        (is (= {} (:headers @*ring-response))))

      (testing "add first value"
        (.addHeader response "foo" "bar")
        (is (= "bar" (.getHeader response "foo")))
        (is (= ["bar"] (.getHeaders response "foo")))
        (is (= {"foo" ["bar"]} (:headers @*ring-response))))

      (testing "add second value"
        (.addHeader response "foo" "gazonk")
        (is (= "bar" (.getHeader response "foo")))
        (is (= ["bar" "gazonk"] (.getHeaders response "foo")))
        (is (= {"foo" ["bar" "gazonk"]} (:headers @*ring-response))))))

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
      (testing "no attribute"
        (is (nil? (.getAttribute session "foo")))
        (is (= {:session {}}
               (select-keys @*ring-response [:session]))))

      (testing "set attribute"
        (.setAttribute session "foo" "bar")
        (is (= "bar" (.getAttribute session "foo")))
        (is (= {:session {::auth0/servlet {"foo" "bar"}}}
               (select-keys @*ring-response [:session]))))

      (testing "set attribute again"
        (.setAttribute session "foo" "gazonk")
        (is (= "gazonk" (.getAttribute session "foo")))
        (is (= {:session {::auth0/servlet {"foo" "gazonk"}}}
               (select-keys @*ring-response [:session]))))

      (testing "remove attribute"
        (.setAttribute session "unrelated" "should not be removed")
        (is (some? (.getAttribute session "foo")))

        (.removeAttribute session "foo")

        (is (nil? (.getAttribute session "foo")))
        (is (= {:session {::auth0/servlet {"unrelated" "should not be removed"}}}
               (select-keys @*ring-response [:session])))))

    (let [[^HttpServletRequest request _ *ring-response] (auth0/ring->servlet {:session {::auth0/servlet {"foo" "bar"}}})
          session (.getSession request true)]
      (testing "read attribute from existing session"
        (is (= "bar" (.getAttribute session "foo")))
        (is (= {:session {::auth0/servlet {"foo" "bar"}}}
               (select-keys @*ring-response [:session])))))))
