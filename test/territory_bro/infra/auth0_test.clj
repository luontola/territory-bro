;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.auth0-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [ring.middleware.cookies :as cookies]
            [ring.middleware.http-response :refer [wrap-http-response]]
            [ring.util.codec :as codec]
            [ring.util.http-predicates :as http-predicates]
            [ring.util.response :as response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.jwt-test :as jwt-test]
            [territory-bro.test.testutil :refer [replace-in]])
  (:import (com.auth0 AuthenticationController IdentityVerificationException Tokens)
           (java.net URL)
           (java.util UUID)
           (javax.servlet.http Cookie HttpServletRequest HttpServletResponse)
           (org.mockito Mockito)))

(defn config-fixture [f]
  (mount/start #'config/env
               #'auth0/auth-controller)
  (f)
  (mount/stop))

(use-fixtures :once config-fixture)

(deftest wrap-redirect-to-login-test
  (let [handler (-> (fn [_]
                      (ring.util.http-response/unauthorized))
                    auth0/wrap-redirect-to-login)]

    (testing "sets the return-to-url"
      (is (= {:status 303
              :headers {"Location" "/login?return-to-url=%2Fsome%2Fpage"}
              :body ""}
             (handler {:request-method :get
                       :uri "/some/page"
                       :query-string nil}))))

    (testing "supports query parameters in the return-to-url"
      (is (= {:status 303
              :headers {"Location" "/login?return-to-url=%2Fsome%2Fpage%3Ffoo%3Dbar%26gazonk"}
              :body ""}
             (handler {:request-method :get
                       :uri "/some/page"
                       :query-string "foo=bar&gazonk"}))))))

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
      (is (= {:redirect_uri "http://localhost:8080/login-callback"
              :client_id "8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi"
              :response_type "code"
              :scope "openid email profile"
              :prompt "select_account"}
             (dissoc query-params :state))))

    (testing "saves OIDC state in session"
      (let [state (:state query-params)]
        (is (not (str/blank? state)))
        (is (= [(str "com.auth0.state=" state "; HttpOnly; Max-Age=600; SameSite=Lax")]
               (response/get-header response "Set-Cookie")))
        (is (= {::auth0/servlet {"com.auth0.state" state
                                 "com.auth0.nonce" nil}} ; nonce is not needed in authorization code flow, see https://community.auth0.com/t/is-nonce-requried-for-the-authoziation-code-flow/111419
               (:session response)))))

    (testing "forwards return-to-url to the login callback"
      (let [request {:request-method :get
                     :uri "/login"
                     :params {:return-to-url "/some/page?foo=bar&gazonk"}
                     :headers {}}
            response (auth0/login-handler request)
            location (URL. (response/get-header response "Location"))
            query-params (-> (codec/form-decode (.getQuery location))
                             (update-keys keyword))
            callback-url (:redirect_uri query-params)]
        (is (= "http://localhost:8080/login-callback?return-to-url=%2Fsome%2Fpage%3Ffoo%3Dbar%26gazonk"
               callback-url))))))


(deftest login-callback-handler-test
  (binding [auth0/auth-controller (Mockito/mock ^Class AuthenticationController)
            dmz/save-user-from-jwt! (fn [jwt]
                                      (is (= {:name "Esko Luontola"}
                                             (select-keys jwt [:name])))
                                      (UUID. 0 1))]
    (let [request {:request-method :get
                   :scheme :http
                   :server-name "localhost"
                   :server-port 8080
                   :uri "/login-callback"
                   :params {:code "mjuZmU8Tw3WO9U4n6No3PJ1g3kTJzYoEYX2nfK8_0U8wY"
                            :state "XJ3KBCcGcXmLnH09gb2AVsQp9bpjiVxLXvo5N4SKEqw"}
                   :cookies {"com.auth0.state" {:value "XJ3KBCcGcXmLnH09gb2AVsQp9bpjiVxLXvo5N4SKEqw"}}
                   :session {::auth0/servlet {"com.auth0.state" "XJ3KBCcGcXmLnH09gb2AVsQp9bpjiVxLXvo5N4SKEqw",
                                              "com.auth0.nonce" nil}}}
          tokens (Tokens. "the-access-token" jwt-test/token nil nil nil)]

      (testing "successful login"
        (-> (Mockito/when (.handle auth0/auth-controller (Mockito/any) (Mockito/any)))
            (.thenReturn tokens))
        (let [response (auth0/login-callback-handler request)]
          (is (= {:status 303,
                  :headers {"Location" "/"},
                  :body "",
                  :session {::auth0/servlet {"com.auth0.state" "XJ3KBCcGcXmLnH09gb2AVsQp9bpjiVxLXvo5N4SKEqw",
                                             "com.auth0.nonce" nil}
                            ::auth0/tokens tokens
                            ::auth/user {:user/id (UUID. 0 1)
                                         :sub "google-oauth2|102883237794451111459"
                                         :name "Esko Luontola"
                                         :nickname "esko.luontola"
                                         :picture "https://lh6.googleusercontent.com/-AmDv-VVhQBU/AAAAAAAAAAI/AAAAAAAAAeI/bHP8lVNY1aA/photo.jpg"}}}
                 response))))

      (testing "redirects to return-to-url instead of home page if provided"
        (let [request (assoc-in request [:params :return-to-url] "/some/page?foo=bar&gazonk")
              response (auth0/login-callback-handler request)]
          (is (= {:status 303
                  :headers {"Location" "/some/page?foo=bar&gazonk"}}
                 (select-keys response [:status :headers])))))

      (testing "failed login"
        (-> (Mockito/when (.handle auth0/auth-controller (Mockito/any) (Mockito/any)))
            (.thenThrow ^Class IdentityVerificationException))
        (let [response (auth0/login-callback-handler request)]
          (is (= {:status 403
                  :headers {}
                  :body "Login failed"}
                 response)))))))


(deftest dev-login-handler-test
  (binding [dmz/save-user-from-jwt! (fn [jwt]
                                      (is (= {:sub "developer"
                                              :name "Developer"
                                              :email "dev@example.com"}
                                             jwt))
                                      (UUID. 0 1))]
    (let [request {:params {:sub "developer"
                            :name "Developer"
                            :email "dev@example.com"}
                   :session {::existing-session-state true}}
          handler (-> auth0/dev-login-handler
                      wrap-http-response)]

      (testing "cannot login in prod mode"
        (let [response (handler request)]
          (is (= {:status 403,
                  :headers {}
                  :body "Dev mode disabled"}
                 response))))

      (testing "can login in dev mode"
        (binding [config/env (replace-in config/env [:dev] false true)]
          (let [response (handler request)]
            (is (= {:status 303
                    :headers {"Location" "/"}
                    :body ""
                    :session {::existing-session-state true
                              ::auth/user {:user/id (UUID. 0 1)
                                           :sub "developer"
                                           :name "Developer"
                                           :email "dev@example.com"}}}
                   response)))))

      (testing "reports mandatory parameters"
        (binding [config/env (replace-in config/env [:dev] false true)]
          (let [request (assoc request :params {})
                response (handler request)]
            (is (= {:status 400
                    :headers {}
                    :body "Missing parameters: email, name, sub"}
                   response))))))))


(deftest logout-handler-test
  (let [request {:request-method :get
                 :uri "/logout"
                 :headers {}
                 :session {::auth/user {:user/id (UUID. 0 1)}}}
        response (auth0/logout-handler request)]

    (testing "clears the application session"
      (is (= {:session nil}
             (select-keys response [:session]))))

    (testing "redirects to the OIDC logout endpoint, to log out of Auth0, and then return to application home page"
      ;; See https://auth0.com/docs/authenticate/login/logout
      ;;     https://auth0.com/docs/authenticate/login/logout/log-users-out-of-auth0
      ;;     https://auth0.com/docs/api/authentication#oidc-logout
      (is (= {:status 303
              :headers {"Location" "https://luontola.eu.auth0.com/v2/logout?returnTo=http://localhost:8080/&client_id=8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi"}}
             (select-keys response [:status :headers]))))))


(deftest ring->servlet-test
  (testing "request URL"
    ;; Servlet spec: The returned URL contains a protocol, server name, port number,
    ;; and server path, but it does not include query string parameters.
    #_(testing "http, custom port"
        (let [[^HttpServletRequest request _ _] (auth0/ring->servlet {:scheme :http
                                                                      :server-name "localhost"
                                                                      :server-port 8080
                                                                      :uri "/the/path"})]
          (is (= "http://localhost:8080/the/path" (str (.getRequestURL request))))))

    #_(testing "https, default port"
        (let [[^HttpServletRequest request _ _] (auth0/ring->servlet {:scheme :https
                                                                      :server-name "example.com"
                                                                      :server-port 443
                                                                      :uri "/"})]
          (is (= "https://example.com/" (str (.getRequestURL request))))))

    (testing "always uses the public-url from config, instead of the scheme/server-name/server-port from the request"
      (let [[^HttpServletRequest request _ _] (auth0/ring->servlet {:scheme :http
                                                                    :server-name "whatever"
                                                                    :server-port 42
                                                                    :uri "/the/path"})]
        (binding [config/env (replace-in config/env [:public-url] "http://localhost:8080" "https://example.com")]
          (is (= "https://example.com/the/path" (str (.getRequestURL request))))))))

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
                    (map (juxt Cookie/.getName Cookie/.getValue))))))))

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
