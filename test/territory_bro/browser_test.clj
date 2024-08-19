;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:e2e territory-bro.browser-test
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [etaoin.api :as b]
            [etaoin.api2 :as b2]
            [hikari-cp.core :as hikari-cp]
            [reitit.core :as reitit]
            [territory-bro.test.testutil :refer [thrown-with-msg?]]
            [territory-bro.ui :as ui])
  (:import (java.util UUID)
           (org.apache.commons.io FileUtils)
           (org.postgresql.util PSQLException)))

(def ^:dynamic *base-url* (or (System/getenv "BASE_URL") "http://localhost:8080"))
(def ^:dynamic *driver*)

(def auth0-username "browser-test@example.com")
(def auth0-password "m6ER6MU7bBYEHrByt8lyop3cG1W811r2")

(def postmortem-dir (io/file "target/etaoin-postmortem"))
(def download-dir (io/file "target/etaoin-download"))
(def browser-config
  {:size [1920 1080]
   :download-dir download-dir})

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
                      (FileUtils/deleteDirectory download-dir)
                      (f)))

(use-fixtures :each with-browser)


(def h1 {:tag :h1})

(defn wait-and-click [driver q]
  (doto driver
    (b/wait-visible q)
    (b/click q)))

(defn clipboard-content [driver]
  ;; Headless Chrome won't modify the system clipboard, so the browser tests
  ;; don't have a direct method for observing what was copied to clipboard.
  (b/js-execute driver "return window.latestCopyToClipboard"))

(defn submit-auth0-login-form [driver]
  (doto driver
    (b/wait-visible :username)
    (b/fill :username auth0-username)
    (b/fill :password auth0-password)
    (wait-and-click {:css "button[type=submit]"})))

(defn do-auth0-login [driver]
  (doto driver
    (wait-and-click :login-button)
    (submit-auth0-login-form)
    (b/wait-visible :logout-button)))

(defn do-dev-login [driver]
  (doto driver
    (wait-and-click :dev-login-button)
    (b/wait-visible :logout-button)))

(defn dev-login-as [driver name]
  (let [username (-> name
                     (str/replace #"\s" "")
                     (str/lower-case))
        params (http/generate-query-string {"sub" username
                                            "name" name
                                            "email" (str username "@example.com")})]
    (b/delete-cookies driver)
    (doto driver
      (b/go (str *base-url* (str "/dev-login?" params)))
      (b/wait-visible :logout-button))))

(defn go-to-any-congregation [driver]
  (let [link [:congregation-list {:tag :a}]
        _ (b/wait-visible driver link)
        congregation-name (b/get-element-text driver link)]
    (doto driver
      (wait-and-click link)
      (b/wait-has-text h1 congregation-name))))

(defn go-to-page [driver title]
  (let [link {:tag :a, :fn/has-string title}]
    (doto driver
      (wait-and-click link)
      (b/wait-has-text h1 title))))

(defn go-to-territory [driver territory-number]
  (doto driver
    (wait-and-click [:territory-list {:tag :a, :fn/text territory-number}])
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
          (wait-and-click :logout-button)
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
    (testing "default language is English"
      (doto *driver*
        (b/go *base-url*)
        (b/wait-visible [{:tag :nav} {:tag :a, :fn/has-string "Home"}])))

    (testing "user can change the language"
      (doto *driver*
        (wait-and-click :language-selection)
        (wait-and-click [:language-selection {:tag :option, :fn/has-string "suomi - Finnish"}])
        (b/wait-visible [{:tag :nav} {:tag :a, :fn/has-string "Etusivu"}])))

    (testing "also error pages are translated"
      (doto *driver*
        (b/go (str *base-url* "/foo"))
        (b/wait-has-text h1 "Sivua ei löytynyt")))))


(deftest demo-test
  (with-per-test-postmortem
    (testing "view demo congregation"
      (doto *driver*
        (b/go *base-url*)
        (wait-and-click {:tag :a, :fn/has-string "View a demo"})
        (b/wait-has-text h1 "Demo Congregation")))

    (testing "view territories list"
      (doto *driver*
        (wait-and-click [{:tag :nav} {:tag :a, :fn/has-string "Territories"}])
        (b/wait-has-text h1 "Territories")))

    (testing "view one territory"
      (doto *driver*
        (wait-and-click [:territory-list {:tag :a}])
        (b/wait-has-text h1 "Territory")))

    (testing "share a link"
      (doto *driver*
        (wait-and-click {:tag :button, :fn/has-string "Share a link"})
        (wait-and-click :copy-share-link))
      (let [original-url (b/get-url *driver*)
            share-link (b/get-element-value *driver* :share-link)]
        (is (str/includes? share-link "/share/demo-")
            "generates a demo share link")
        (is (= share-link (clipboard-content *driver*))
            "can copy to clipboard")

        (testing "- open the share"
          (doto *driver*
            (b/go "about:blank")
            (b/go share-link))
          (is (= original-url (b/get-url *driver*))))))

    (testing "view printouts"
      (doto *driver*
        (wait-and-click [{:tag :nav} {:tag :a, :fn/has-string "Printouts"}])
        (b/wait-has-text h1 "Printouts")))

    (testing "view support page"
      (doto *driver*
        (wait-and-click [{:tag :nav} {:tag :a, :fn/has-string "Support"}])
        (b/wait-has-text h1 "Support")))

    (testing "cannot view settings"
      (let [settings-path (-> ui/router
                              (reitit/match-by-name :territory-bro.ui.settings-page/page {:congregation "demo"})
                              (reitit/match->path))]
        (is (= "/congregation/demo/settings" settings-path))
        (doto *driver*
          (b/go (str *base-url* settings-path))
          (b/wait-has-text h1 "Welcome")) ; access denied, so redirects the anonymous user to Auth0 login screen
        (is (str/includes? (b/get-url *driver*) ".auth0.com/"))))))


(deftest registration-test
  (with-per-test-postmortem
    (let [nonce (System/currentTimeMillis)
          user1 (str "Test User1 " nonce)
          user2 (str "Test User2 " nonce)
          *user2-id (atom nil)
          congregation-name (str "Test Congregation " nonce)
          *congregation-url (atom nil)]

      (testing "register new congregation"
        (doto *driver*
          (dev-login-as user1)
          (go-to-page "Register a new congregation")

          (b/fill :congregation-name congregation-name)
          (wait-and-click {:tag :button, :fn/text "Register"})

          ;; we should arrive at the newly created congregation's front page
          (b/wait-has-text h1 congregation-name))
        (reset! *congregation-url (b/get-url *driver*)))

      ;; TODO: extract the above to a shared test setup, so that all tests will use the same congregation as test data
      ;; TODO: extract the below as user-management-test?

      (testing "find out user2's ID"
        (doto *driver*
          (dev-login-as user2)
          (go-to-page "Join an existing congregation")
          (wait-and-click :copy-your-user-id))
        (let [user2-id (reset! *user2-id (b/get-element-text *driver* :your-user-id))]
          (is (some? (parse-uuid user2-id))
              "shows the user ID")
          (is (= user2-id (clipboard-content *driver*))
              "can copy to clipboard")))

      (testing "add user2 to congregation"
        (doto *driver*
          (dev-login-as user1)
          (b/go @*congregation-url)
          (b/wait-has-text h1 congregation-name)
          (go-to-page "Settings")

          (b/fill [:users-section :user-id] @*user2-id)
          (wait-and-click [:users-section {:tag :button, :fn/text "Add user"}])
          (b/wait-visible [:users-section {:tag :tr, :fn/has-string user2}])))

      (testing "user2 can view congregation after being added"
        (doto *driver*
          (dev-login-as user2)
          (b/go @*congregation-url)
          (b/wait-has-text h1 congregation-name)))

      (testing "remove user2 from congregation"
        (doto *driver*
          (dev-login-as user1)
          (b/go @*congregation-url)
          (b/wait-has-text h1 congregation-name)
          (go-to-page "Settings")

          (wait-and-click [:users-section {:tag :tr, :fn/has-string user2} {:tag :button, :fn/text "Remove user"}])
          (b/wait-invisible [:users-section {:tag :tr, :fn/has-string user2}])))

      (testing "user2 cannot view congregation after being removed"
        (doto *driver*
          (dev-login-as user2)
          (b/go @*congregation-url)
          (b/wait-has-text h1 "Access denied"))))))


(deftest settings-test
  (with-per-test-postmortem
    (let [nonce (System/currentTimeMillis)
          user (str "Test User " nonce)
          congregation-name (str "Test Congregation " nonce)]

      (testing "register new congregation"
        (doto *driver*
          (dev-login-as user)
          (go-to-page "Register a new congregation")

          (b/fill :congregation-name congregation-name)
          (wait-and-click {:tag :button, :fn/text "Register"})

          ;; we should arrive at the newly created congregation's front page
          (b/wait-has-text h1 congregation-name)))

      ;; TODO: extract the above to a shared test setup, so that all tests will use the same congregation as test data

      (let [qgis-project (io/file download-dir (str congregation-name ".qgs"))]
        (testing "download QGIS project file"
          (doto *driver*
            (go-to-page "Settings")
            (wait-and-click {:tag :a, :fn/text "Download QGIS project file"}))
          (b/wait-predicate #(.isFile qgis-project)
                            {:message (str "Wait until file exists: " qgis-project)}))

        (testing "access GIS database"
          (let [[_ dbname host port user password schema] (re-find #"dbname='(\w+)' host=(\w+) port=(\w+) user='(\w+)' password='(.*?)' .* table=\"(\w+)\"\."
                                                                   (slurp qgis-project))
                jdbc-url (str "jdbc:postgresql://" host ":" port "/" dbname "?user=" user "&password=" password)]
            (is (str/starts-with? user "gis_user_"))
            (is (str/starts-with? schema "territorybro_"))
            (with-open [datasource (hikari-cp/make-datasource {:jdbc-url jdbc-url})]
              (jdbc/with-db-connection [conn {:datasource datasource}]
                ;; TODO: test writing GIS data
                (is (= [] (jdbc/query conn [(str "select * from " schema ".territory limit 1")]))
                    "can access the congregation's schema")
                (is (thrown-with-msg? PSQLException
                                      #"ERROR: permission denied for schema territorybro"
                                      (jdbc/query conn ["select * from territorybro.event limit 1"]))
                    "cannot access other schemas"))))))

      (testing "edit congregation settings"
        (doto *driver*
          (go-to-page "Settings")
          (b/fill :congregation-name " B")
          (wait-and-click {:tag :button, :fn/text "Save settings"})
          (b/wait-visible [{:tag :nav} {:tag :a, :fn/has-string (str congregation-name " B")}]))))))


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
      (go-to-page "Territories"))

    (let [[shared-territory unrelated-territory] (two-random-territories *driver*)]
      (is (not (str/blank? (:number shared-territory))))
      (is (not (str/blank? (:number unrelated-territory))))
      (go-to-territory *driver* (:number shared-territory))

      (doto *driver*
        (wait-and-click {:tag :button, :fn/has-string "Share a link"})
        (wait-and-click :copy-share-link))
      (let [share-link (b/get-element-value *driver* :share-link)]
        (testing "share a link"
          (is (str/starts-with? share-link (str *base-url* "/share/"))
              "generates a share link")
          (is (= share-link (clipboard-content *driver*))
              "can copy to clipboard"))

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
          (go-to-page *driver* "Territories")
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

(deftest edit-do-not-calls-test
  (with-per-test-postmortem
    (doto *driver*
      (b/go *base-url*)
      (do-dev-login)
      ;; XXX: this test assumes that the test user already has a congregation and territories
      ;; TODO: test registration and adding territories; use that as test data for other tests
      (go-to-any-congregation)
      (go-to-page "Territories"))

    (testing "can edit do-not-calls"
      (let [test-content (str "test content " (UUID/randomUUID))
            input-field [:do-not-calls {:tag :textarea}]]
        (doto *driver*
          (wait-and-click [:territory-list {:tag :a}])
          (wait-and-click [:do-not-calls {:tag :button, :fn/has-string "Edit"}])
          (b/wait-visible input-field)
          (b/clear input-field)
          (b/fill input-field test-content)
          (wait-and-click [:do-not-calls {:tag :button, :fn/has-string "Save"}])
          (b/wait-invisible input-field)
          (b/wait-has-text :do-not-calls test-content))))))


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
             (b/get-element-text *driver* h1))))

    (testing "error pages show the user's authentication status"
      (is (b/visible? *driver* :logout-button)))))

(deftest sudo-test
  (with-per-test-postmortem
    (testing "regular users cannot use sudo"
      (doto *driver*
        (b/go *base-url*)
        (dev-login-as "Regular User")
        (b/go (str *base-url* "/sudo"))
        (b/wait-visible h1))
      (is (= "Access denied 🛑"
             (b/get-element-text *driver* h1))))

    (testing "super users can use sudo"
      (doto *driver*
        (wait-and-click :logout-button)
        (do-dev-login)
        (b/go (str *base-url* "/sudo"))
        (b/wait-visible h1))
      (is (= "Territory Bro"
             (b/get-element-text *driver* h1))))))
