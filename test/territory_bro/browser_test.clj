;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:e2e territory-bro.browser-test
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [etaoin.api :as b]
            [etaoin.api2 :as b2])
  (:import (java.util UUID)
           (org.apache.commons.io FileUtils)))

(def ^:dynamic *base-url* (or (System/getenv "BASE_URL") "http://localhost:8080"))
(def ^:dynamic *driver*)

(def auth0-username "browser-test@example.com")
(def auth0-password "m6ER6MU7bBYEHrByt8lyop3cG1W811r2")

(def browser-config
  {:size [1920 1080]
   ;; TODO: figure out how to enable reading what the browser copied to clipboard
   #_#_:prefs {:profile.content_settings.exceptions.clipboard {"*" {"setting" 1
                                                                    "last_modified" (System/currentTimeMillis)}}}})
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
  (is true) ; disable Kaocha's warning about missing `is` - Etaoin's waits are like assertions without an `is`
  (b2/with-chrome-headless [driver browser-config]
    (binding [*driver* driver]
      (f))))

(use-fixtures :once (fn [f]
                      (FileUtils/deleteDirectory postmortem-dir)
                      (f)))

(use-fixtures :each with-browser)


(def h1 {:tag :h1})

(defn submit-auth0-login-form [driver]
  (doto driver
    (b/wait-visible :username)
    (b/fill :username auth0-username)
    (b/fill :password auth0-password)
    (b/click {:css "button[type=submit]"})))

(defn do-auth0-login [driver]
  (doto driver
    (b/wait-visible :login-button)
    (b/click :login-button)
    (submit-auth0-login-form)
    (b/wait-visible :logout-button)))

(defn do-dev-login [driver]
  (doto driver
    (b/wait-visible :dev-login-button)
    (b/click :dev-login-button)
    (b/wait-visible :logout-button)))

(defn dev-login-as [driver name]
  (let [username (-> name
                     (str/replace #"\s" "")
                     (str/lower-case))
        params (http/generate-query-string {"sub" username
                                            "name" name
                                            "email" (str username "@example.com")})]
    (if (str/ends-with? *base-url* ":8080") ; TODO: remove this conditional together with SPA UI
      (doto driver ; SPA UI
        (b/go (str *base-url* (str "/api/dev-login?" params)))
        (b/wait-visible {:fn/text "Logged in"})
        (b/go *base-url*)
        (b/wait-visible :logout-button))
      (doto driver ; SSR UI
        (b/go (str *base-url* (str "/dev-login?" params)))
        (b/wait-visible :logout-button)))))

(defn go-to-any-congregation [driver]
  (let [link [:congregation-list {:tag :a}]
        _ (b/wait-visible driver link)
        congregation-name (b/get-element-text driver link)]
    (doto driver
      (b/click link)
      (b/wait-has-text h1 congregation-name))))

(defn go-to-territory-list [driver]
  (let [link [{:tag :nav} {:tag :a, :fn/has-string "Territories"}]]
    (doto driver
      (b/wait-visible link)
      (b/click link)
      (b/wait-has-text h1 "Territories"))))

(defn go-to-territory [driver territory-number]
  (doto driver
    (b/wait-visible :territory-list)
    (b/click [:territory-list {:tag :a, :fn/text territory-number}])
    (b/wait-has-text h1 (str "Territory " territory-number))))


(deftest login-and-logout-test
  (with-per-test-postmortem
    (let [original-page-url (str *base-url* "/support?foo=bar&gazonk")] ; also query string should be restored

      (testing "login with Auth0"
        (doto *driver*
          (b/go original-page-url)
          (do-auth0-login)))

      (testing "shows user is logged in"
        (is (b/has-text? *driver* {:css "nav"} "Logout"))
        (is (b/has-text? *driver* {:css "nav"} auth0-username)))

      (testing "after login, redirects to the page where the user came from"
        (is (= original-page-url (b/get-url *driver*))))

      (testing "logout"
        (doto *driver*
          (b/click :logout-button)
          (b/wait-visible :login-button)))

      (testing "shows the user is logged out"
        (is (b/has-text? *driver* {:css "nav"} "Login"))))))


(deftest login-via-entering-restricted-page-test
  (with-per-test-postmortem
    (let [restricted-page-url (str *base-url* "/congregation/" (UUID/randomUUID) "?foo=bar&gazonk")] ; also query string should be restored

      (testing "enter restricted page, redirects to login"
        (doto *driver*
          (b/go restricted-page-url)
          (submit-auth0-login-form)))

      ;; TODO: don't use a random congregation ID, but open an existing congregation for a more common use case
      (b/wait-has-text *driver* h1 "Access denied")

      (testing "after login, redirects to the page which the user originally entered"
        (is (= restricted-page-url (b/get-url *driver*)))))))


(deftest change-language-test
  (with-per-test-postmortem
    (doto *driver*
      (b/go *base-url*)
      (b/wait-visible :language-selection))

    (testing "default language is English"
      (is (b/visible? *driver* [{:tag :nav} {:tag :a, :fn/has-string "Home"}])))

    (testing "user can change the language"
      (doto *driver*
        (b/click :language-selection)
        (b/click [:language-selection {:tag :option, :fn/has-string "suomi - Finnish"}])
        (b/wait-visible [{:tag :nav} {:tag :a, :fn/has-string "Etusivu"}])))))


(deftest registration-test
  (with-per-test-postmortem
    (let [nonce (System/currentTimeMillis)
          user (str "Test User " nonce)
          congregation-name (str "Test Congregation " nonce)]
      (doto *driver*
        (dev-login-as user)

        (b/click {:tag :a, :fn/has-string "Register a new congregation"})
        (b/wait-visible {:tag :h1, :fn/has-string "Register a new congregation"})
        (b/fill :congregationName congregation-name)
        (b/click {:tag :button, :fn/has-string "Register"})

        ;; we should arrive at the newly created congregation's front page
        (b/wait-visible {:tag :h1, :fn/has-string congregation-name})))))


(defn- two-random-territories [driver]
  (->> (b/query-all driver [:territory-list {:tag :a}])
       (shuffle) ; pick the territories randomly
       (into nil) ; de-chunk to avoid calling get-element-text-el more than twice
       (map (fn [el]
              {:number (b/get-element-text-el driver el)
               :link (b/get-element-attr-el driver el :href)}))
       (take 2)))

(deftest share-territory-link-test
  (with-per-test-postmortem
    (doto *driver*
      (b/go *base-url*)
      (do-dev-login)
      ;; XXX: this test assumes that the test user already has a congregation and territories
      ;; TODO: test registration and adding territories; use that as test data for other tests
      (go-to-any-congregation)
      (go-to-territory-list))

    (let [[shared-territory unrelated-territory] (two-random-territories *driver*)]
      (is (not (str/blank? (:number shared-territory))))
      (is (not (str/blank? (:number unrelated-territory))))
      (go-to-territory *driver* (:number shared-territory))

      (doto *driver*
        (b/click {:tag :button, :fn/has-string "Share a link"})
        (b/wait-visible :copy-share-link)
        (b/click :copy-share-link))
      (let [share-link (b/js-execute *driver* "return window.latestCopyToClipboard")]
        (is (str/starts-with? share-link (str *base-url* "/share/"))
            "generates a share link")

        (testing "open shared link as anonymous user"
          (doto *driver*
            (b/delete-cookies)
            (b/go share-link)
            (b/wait-has-text h1 "Territory"))
          (is (= (str "Territory " (:number shared-territory))
                 (b/get-element-text *driver* h1))
              "user can view the shared territory")
          (is (b/visible? *driver* :login-button)
              "user is not logged in"))

        (testing "cannot see territories which were not shared"
          (doto *driver*
            (go-to-territory-list))
          (is (= [(:number shared-territory)]
                 (->> (b/query-all *driver* [:territory-list {:tag :a}])
                      (map #(b/get-element-text-el *driver* %))))
              "page lists only the shared territory")
          (is (nil? (b/wait-has-text-everywhere *driver* "Only those territories which have been shared with you are currently shown."))
              "page contains a disclaimer")

          (doto *driver*
            (b/go (:link unrelated-territory))
            (b/wait-visible :username))
          (is (= "Log in | Territory Bro (Dev)"
                 (b/get-title *driver*))
              "trying to view an unrelated territory of the same congregation requires logging in"))))))


(deftest error-pages-test
  (with-per-test-postmortem
    (doto *driver*
      (b/go *base-url*)
      (do-dev-login)) ; login to avoid 401 Unauthorized when testing for 403 Forbidden

    (testing "404 Not Found"
      (doto *driver*
        (b/go (str *base-url* "/foo"))
        (b/wait-visible h1))
      (is (= "Page not found 😵"
             (b/get-element-text *driver* h1))))

    (testing "403 Forbidden"
      (doto *driver*
        (b/go (str *base-url* "/congregation/" (UUID/randomUUID)))
        (b/wait-visible h1))
      (is (= "Access denied 🛑"
             (b/get-element-text *driver* h1))))))
