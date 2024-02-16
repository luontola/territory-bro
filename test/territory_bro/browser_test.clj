;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:e2e territory-bro.browser-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [etaoin.api :as b]
            [etaoin.api2 :as b2])
  (:import (org.apache.commons.io FileUtils)))

(def ^:dynamic *base-url* "http://localhost:8080") ; TODO: parameterize the url to run against any deployment
(def ^:dynamic *driver*)

(def auth0-username "browser-test@example.com")
(def auth0-password "m6ER6MU7bBYEHrByt8lyop3cG1W811r2")

(def browser-config {:size [1920 1080]})
(def postmortem-dir (io/file "target/etaoin-postmortem"))

(defn screenshot! []
  (let [var (first *testing-vars*)
        dir (if var
              (io/file postmortem-dir (name (symbol var)))
              postmortem-dir)]
    (b/postmortem-handler *driver* {:dir dir})))

(defn with-browser [f]
  (FileUtils/deleteDirectory postmortem-dir)
  (b2/with-chrome-headless [driver browser-config]
    (binding [*driver* driver]
      (f))))

(use-fixtures :each with-browser)


(defn click-login [driver username password]
  (doto driver
    (b/wait-visible :login-button)
    (b/click :login-button)

    (b/wait-visible :1-email)
    (b/fill :1-email username)
    (b/fill :1-password password)
    (b/click :1-submit)

    (b/wait-visible :logout-button)))

(deftest login-test
  (testing "login with username and password"
    (doto *driver*
      (b/go *base-url*)
      (click-login auth0-username auth0-password)))

  (testing "shows user is logged in"
    (is (b/has-text? *driver* {:css "nav"} "Logout"))
    (is (b/has-text? *driver* {:css "nav"} auth0-username))
    (screenshot!)))
