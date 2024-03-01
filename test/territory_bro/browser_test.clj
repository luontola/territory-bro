;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:e2e territory-bro.browser-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [etaoin.api :as b]
            [etaoin.api2 :as b2])
  (:import (java.util UUID)
           (org.apache.commons.io FileUtils)))

(def ^:dynamic *base-url* (or (System/getenv "BASE_URL") "http://localhost:8080"))
(def ^:dynamic *driver*)

(def auth0-username "browser-test@example.com")
(def auth0-password "m6ER6MU7bBYEHrByt8lyop3cG1W811r2")

(def browser-config {:size [1920 1080]})
(def postmortem-dir (io/file "target/etaoin-postmortem"))

(defn per-test-subdir [parent-dir]
  (let [var (first *testing-vars*)]
    (if var
      (io/file parent-dir (name (symbol var)))
      parent-dir)))

(defmacro with-per-test-postmortem [& body]
  `(let [opts# {:dir (per-test-subdir postmortem-dir)}]
     (b/with-postmortem *driver* opts#
       ~@body
       ;; take a screenshot at the end, even when the test succeeds
       (b/postmortem-handler *driver* opts#))))

(defn with-browser [f]
  (b2/with-chrome-headless [driver browser-config]
    (binding [*driver* driver]
      (f))))

(use-fixtures :once (fn [f]
                      (FileUtils/deleteDirectory postmortem-dir)
                      (f)))

(use-fixtures :each with-browser)


(defn submit-auth0-login-form [driver]
  (doto driver
    (b/wait-visible :1-email)
    (b/fill :1-email auth0-username)
    (b/fill :1-password auth0-password)
    (b/click :1-submit)))

(deftest login-and-logout-test
  (with-per-test-postmortem
    (testing "login with username and password"
      (doto *driver*
        (b/go *base-url*)
        (b/wait-visible :login-button)
        (b/click :login-button)
        (submit-auth0-login-form)
        (b/wait-visible :logout-button)))

    (testing "shows user is logged in"
      (is (b/has-text? *driver* {:css "nav"} "Logout"))
      (is (b/has-text? *driver* {:css "nav"} auth0-username)))

    (testing "logout"
      (doto *driver*
        (b/click :logout-button)
        (b/wait-visible :login-button)))

    (testing "shows the user is logged out"
      (is (b/has-text? *driver* {:css "nav"} "Login")))))

(deftest login-via-entering-restricted-page-test
  (with-per-test-postmortem
    (let [restricted-page-url (str *base-url* "/congregation/" (UUID/randomUUID) "?foo=bar&gazonk")]
      (testing "enter restricted page, redirects to login"
        (doto *driver*
          (b/go restricted-page-url)
          (submit-auth0-login-form)))

      ;; TODO: don't use a random congregation ID, but open an existing congregation for a more common use case
      (try
        (b/wait-has-text *driver* {:css "h1"} "Not authorized")
        ;; XXX: the SSR page doesn't yet have the same content as the SPA page, so this will timeout,
        ;;      but we can safely ignore it, because then we have waited long enough for the correct page to load
        (catch Exception _))

      (testing "after login, redirects to the page which the user originally entered"
        (is (= restricted-page-url (b/get-url *driver*)))))))
