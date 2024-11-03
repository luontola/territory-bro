(ns ^:e2e territory-bro.browser-test
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [etaoin.api :as b]
            [etaoin.api2 :as b2]
            [next.jdbc :as jdbc]
            [reitit.core :as reitit]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.db :as db]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [thrown-with-msg?]]
            [territory-bro.ui :as ui]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-page-test :refer [parse-open-graph-tags]])
  (:import (java.io File)
           (java.time Instant)
           (java.util UUID)
           (org.apache.commons.io FileUtils)
           (org.postgresql.util PSQLException)))

(def ^:dynamic *base-url* (or (System/getenv "BASE_URL") "http://localhost:8080"))
(def ^:dynamic *driver*)
(def ^:dynamic *user*)
(def ^:dynamic *congregation-name*)
(def *congregation-url (atom nil))

(def auth0-username "browser-test@example.com")
(def auth0-password "m6ER6MU7bBYEHrByt8lyop3cG1W811r2")

(def ^File postmortem-dir (io/file "target/etaoin-postmortem"))
(def ^File download-dir (io/file "target/etaoin-download"))
(def browser-config
  {:size [1920 1080]
   :prefs {:download.default_directory (.getAbsolutePath download-dir)}
   :args ["--disable-search-engine-choice-screen"]})

(defn per-test-subdir [parent-dir]
  (let [var (first *testing-vars*)]
    (if var
      (io/file parent-dir (name (symbol var)))
      parent-dir)))

(defn with-per-test-postmortem [f]
  (let [opts {:dir (per-test-subdir postmortem-dir)}]
    (b/with-postmortem *driver* opts
      (f)
      ;; take a screenshot at the end, even when the test succeeds
      (b/postmortem-handler *driver* opts))))

(defn with-browser-config [config f]
  (is true) ; disable Kaocha's warning about missing `is` - Etaoin's waits are like assertions without an `is`
  (b2/with-chrome-headless [driver config]
    (binding [*driver* driver]
      (f))))

(defn with-browser [f]
  (with-browser-config browser-config f))

(defn await-healthy
  ([url]
   (await-healthy url (-> (Instant/now) (.plusSeconds 10))))
  ([url ^Instant deadline]
   (let [status (try
                  (:status (http/get url {:throw-exceptions false}))
                  (catch Exception e ; can throw "java.net.ConnectException: Connection refused"
                    e))]
     (when-not (= 200 status)
       (when (-> (Instant/now) (.isAfter deadline))
         (throw (IllegalStateException. (str url " not healthy; received " status))))
       (Thread/sleep 500)
       (recur url deadline)))))

(use-fixtures :once (fn [f]
                      (await-healthy (str *base-url* "/status"))
                      (FileUtils/deleteDirectory postmortem-dir)
                      (FileUtils/deleteDirectory download-dir)
                      ;; XXX: because of unique nonce for each test run, tests will break if skipping test data setup and running only one test
                      (let [nonce (System/currentTimeMillis)]
                        (binding [*user* (str "Test User " nonce)
                                  *congregation-name* (str "Test Congregation " nonce)]
                          (reset! *congregation-url nil)
                          (f)))))


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

(defn go-to-congregation [driver congregation-name]
  (doto driver
    (wait-and-click [:congregation-list {:tag :a, :fn/has-string congregation-name}])
    (b/wait-has-text h1 congregation-name)))

(defn go-to-page [driver title & [h1-title]]
  (let [link {:tag :a, :fn/has-string title}]
    (doto driver
      (wait-and-click link)
      (b/wait-has-text h1 (or h1-title title)))))

(defn go-to-territory [driver territory-number]
  (doto driver
    (wait-and-click [:territory-list {:tag :a, :fn/text territory-number}])
    (b/wait-has-text h1 (str "Territory " territory-number))))


;;;; SHARED TEST DATA SETUP

(deftest ^{:order -2} registration-test
  (with-fixtures [with-browser with-per-test-postmortem]
    (testing "register new congregation"
      (doto *driver*
        (dev-login-as *user*)
        (go-to-page "Registration" "Register a new congregation")

        (b/fill :congregation-name *congregation-name*)
        (wait-and-click {:tag :button, :fn/text "Register"})

        ;; we should arrive at the newly created congregation's front page
        (b/wait-has-text h1 *congregation-name*))
      (reset! *congregation-url (b/get-url *driver*))
      (spit "target/test-congregation-id" ; used by CI in build.sh
            (second (re-find #"congregation/([^/]*)" @*congregation-url))))

    (testing "none of the pages crash even when there is no data"
      (doto *driver*
        (go-to-page "Territories")
        (go-to-page "Printouts")
        (go-to-page "Settings")))))


(deftest ^{:order -1} gis-access-test
  (with-fixtures [with-browser with-per-test-postmortem]
    (doto *driver*
      (dev-login-as *user*)
      (go-to-congregation *congregation-name*))

    (let [qgis-project (io/file download-dir (str *congregation-name* ".qgs"))]
      (testing "download QGIS project file"
        (doto *driver*
          (go-to-page "Settings")
          (wait-and-click {:tag :a, :fn/text "Download QGIS project file"}))
        (b/wait-predicate #(.isFile qgis-project)
                          {:message (str "Wait until file exists: " qgis-project)}))

      (let [[_ dbname host port user password schema] (re-find #"dbname='(\w+)' host=(\w+) port=(\w+) user='(\w+)' password='(.*?)' .* table=\"(\w+)\"\."
                                                               (slurp qgis-project))
            jdbc-url (str "jdbc:postgresql://" host ":" port "/" dbname "?user=" user "&password=" password "&currentSchema=" schema ",public")
            congregation-boundary-id (UUID/randomUUID)
            region-id (UUID/randomUUID)
            territory-id1 (UUID/randomUUID)
            territory-id2 (UUID/randomUUID)
            territory-id3 (UUID/randomUUID)]

        (testing "access GIS database"
          (is (str/starts-with? user "gis_user_"))
          (is (str/starts-with? schema "territorybro_"))
          (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
            (is (= [] (db/execute! conn [(str "select * from " schema ".territory limit 1")]))
                "can access the congregation's schema")
            (is (thrown-with-msg? PSQLException
                                  #"ERROR: permission denied for schema territorybro"
                                  (db/execute! conn ["select * from territorybro.event limit 1"]))
                "cannot access other schemas")
            (db/execute-one! conn ["insert into congregation_boundary (id, location) values (?, ?::geography)"
                                   congregation-boundary-id testdata/wkt-helsinki])
            (db/execute-one! conn ["insert into subregion (id, name, location) values (?, ?, ?::geography)"
                                   region-id "South Helsinki" testdata/wkt-south-helsinki])
            (db/execute-one! conn ["insert into territory (id, number, addresses, subregion, location) values (?, ?, ?, ?, ?::geography)"
                                   territory-id1 "101" "Rautatientori" "South Helsinki" testdata/wkt-helsinki-rautatientori])
            (db/execute-one! conn ["insert into territory (id, number, addresses, subregion, location) values (?, ?, ?, ?, ?::geography)"
                                   territory-id2 "102" "Kauppatori" "South Helsinki" testdata/wkt-helsinki-kauppatori])
            (db/execute-one! conn ["insert into territory (id, number, addresses, subregion, location) values (?, ?, ?, ?, ?::geography)"
                                   territory-id3 "103" "Narinkkatori" "South Helsinki" testdata/wkt-helsinki-narinkkatori])))

        (testing "GIS data is synced to the web app"
          (doto *driver*
            (go-to-page "Territories"))
          (when-not (b/has-text? *driver* "101")
            (doto *driver*
              (b/wait 1) ; it shouldn't take longer than this for the changes to be synced
              (b/refresh)
              (b/wait-has-text h1 "Territories")))
          (is (= (html/normalize-whitespace
                  "Number  Region          Addresses
                   101     South Helsinki  Rautatientori
                   102     South Helsinki  Kauppatori
                   103     South Helsinki  Narinkkatori")
                 (html/normalize-whitespace
                  (b/get-element-text *driver* :territory-list)))))))))


;; NORMAL TESTS

(deftest login-and-logout-test
  (with-fixtures [with-browser with-per-test-postmortem]
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
  (with-fixtures [with-browser with-per-test-postmortem]
    (let [restricted-page-url (str @*congregation-url "?foo=bar&gazonk")] ; also query string should be restored

      (testing "enter restricted page, redirects to login"
        (doto *driver*
          (b/go restricted-page-url)
          (submit-auth0-login-form)))

      ;; TODO: open a congregation which the user can access, for a more common use case
      (b/wait-has-text *driver* h1 "Access denied")

      (testing "after login, redirects to the page which the user originally entered"
        (is (= restricted-page-url (b/get-url *driver*)))))))


(defn- accept-languages [languages]
  (assoc-in browser-config [:prefs :intl.accept_languages] languages))

(deftest change-language-test
  (with-fixtures [(partial with-browser-config (accept-languages "es-419,fi-FI,en-US"))
                  with-per-test-postmortem]
    (testing "chooses the language automatically based on browser preferences"
      (doto *driver*
        (b/go *base-url*)
        (b/wait-visible [{:tag :nav} {:tag :a, :fn/has-string "Inicio"}]))))

  (with-fixtures [(partial with-browser-config (accept-languages "xx"))
                  with-per-test-postmortem]
    (testing "the default language is English"
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
        (b/wait-has-text h1 "Sivua ei löydy")))))


(deftest demo-test
  (with-fixtures [with-browser with-per-test-postmortem]
    (testing "view demo congregation"
      (doto *driver*
        (b/go *base-url*)
        (wait-and-click [{:tag :nav} {:tag :a, :fn/has-string "Demo"}])
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


(deftest user-management-test
  (with-fixtures [with-browser with-per-test-postmortem]
    (doto *driver*
      (dev-login-as *user*)
      (go-to-congregation *congregation-name*))
    (let [congregation-url (b/get-url *driver*)
          user2 (str "Test User2 " (System/currentTimeMillis))
          *user2-id (atom nil)]

      (testing "find out user2's ID"
        (doto *driver*
          (dev-login-as user2)
          (go-to-page "Registration" "Register a new congregation")
          (go-to-page "Join an existing congregation")
          (wait-and-click :copy-your-user-id))
        (let [user2-id (reset! *user2-id (b/get-element-text *driver* :your-user-id))]
          (is (some? (parse-uuid user2-id))
              "shows the user ID")
          (is (= user2-id (clipboard-content *driver*))
              "can copy to clipboard")))

      (testing "add user2 to congregation"
        (doto *driver*
          (dev-login-as *user*)
          (b/go congregation-url)
          (b/wait-has-text h1 *congregation-name*)
          (go-to-page "Settings")

          (b/fill [:users-section :user-id] @*user2-id)
          (wait-and-click [:users-section {:tag :button, :fn/text "Add user"}])
          (b/wait-visible [:users-section {:tag :tr, :fn/has-string user2}])))

      (testing "user2 can view congregation after being added"
        (doto *driver*
          (dev-login-as user2)
          (b/go congregation-url)
          (b/wait-has-text h1 *congregation-name*)))

      (testing "remove user2 from congregation"
        (doto *driver*
          (dev-login-as *user*)
          (b/go congregation-url)
          (b/wait-has-text h1 *congregation-name*)
          (go-to-page "Settings")

          (wait-and-click [:users-section {:tag :tr, :fn/has-string user2} {:tag :button, :fn/text "Remove user"}])
          (b/wait-invisible [:users-section {:tag :tr, :fn/has-string user2}])))

      (testing "user2 cannot view congregation after being removed"
        (doto *driver*
          (dev-login-as user2)
          (b/go congregation-url)
          (b/wait-has-text h1 "Access denied"))))))


(deftest settings-test
  (with-fixtures [with-browser with-per-test-postmortem]
    (doto *driver*
      (dev-login-as *user*)
      (go-to-congregation *congregation-name*))

    (testing "edit congregation settings"
      (doto *driver*
        (go-to-page "Settings")
        (b/fill :congregation-name " B") ; adding text to the end should not break go-to-congregation for other tests
        (wait-and-click {:tag :button, :fn/text "Save settings"})
        (b/wait-visible [{:tag :nav} {:tag :a, :fn/has-string (str *congregation-name* " B")}])))))


(defn- get-territory-url [territory-number]
  (let [el (b/query *driver* [:territory-list {:tag :a, :fn/text (str territory-number)}])
        href (b/get-element-attr-el *driver* el :href)]
    (is (not (str/blank? href)))
    (str *base-url* href)))

(deftest share-territory-link-test
  (with-fixtures [with-browser with-per-test-postmortem]
    (doto *driver*
      (dev-login-as *user*)
      (go-to-congregation *congregation-name*)
      (go-to-page "Territories"))

    (let [shared-territory-number "101"
          not-shared-territory-url (get-territory-url "102")]
      (go-to-territory *driver* shared-territory-number)

      (doto *driver*
        (wait-and-click {:tag :button, :fn/has-string "Share a link"})
        (wait-and-click :copy-share-link))
      (let [share-link (b/get-element-value *driver* :share-link)]
        (testing "share a link"
          (is (str/starts-with? share-link (str *base-url* "/share/"))
              "generates a share link")
          (is (= share-link (clipboard-content *driver*))
              "can copy to clipboard"))

        (testing "instant messenger app creates a preview of the shared link"
          (let [response (http/get share-link)]
            (is (= 200 (:status response)))
            (is (str/starts-with?
                 (-> (parse-open-graph-tags (:body response))
                     (get "og:title"))
                 (str "Territory " shared-territory-number " - South Helsinki - " *congregation-name*)))))

        (testing "open shared link as anonymous user"
          (doto *driver*
            (b/delete-cookies)
            (b/go share-link)
            (b/wait-has-text h1 "Territory"))
          (is (= (str "Territory " shared-territory-number)
                 (b/get-element-text *driver* h1))
              "user can view the shared territory")
          (is (b/visible? *driver* :login-button)
              "user is not logged in")
          (is (not (str/includes? (b/get-url *driver*) "?"))
              "the share-key is automatically cleaned up from the query parameters"))

        (testing "cannot see territories which were not shared"
          (go-to-page *driver* "Territories")
          (is (= [shared-territory-number]
                 (->> (b/query-all *driver* [:territory-list {:tag :a}])
                      (map #(b/get-element-text-el *driver* %))))
              "page lists only the shared territory")
          (is (b/wait-has-text-everywhere *driver* "Only those territories which have been shared with you are currently shown.")
              "page contains a disclaimer")

          (doto *driver*
            (b/go not-shared-territory-url)
            (b/wait-visible :username))
          (is (= "Log in | Territory Bro (Dev)"
                 (b/get-title *driver*))
              "trying to view a not shared territory of the same congregation requires logging in"))))))


(deftest edit-do-not-calls-test
  (with-fixtures [with-browser with-per-test-postmortem]
    (doto *driver*
      (dev-login-as *user*)
      (go-to-congregation *congregation-name*)
      (go-to-page "Territories"))

    (testing "can edit do-not-calls"
      (let [test-content (str "test content " (UUID/randomUUID))
            input-field [:do-not-calls {:tag :textarea}]]
        (doto *driver*
          (go-to-territory "101")
          (wait-and-click [:do-not-calls {:tag :button, :fn/has-string "Edit"}])
          (b/wait-visible input-field)
          (b/clear input-field)
          (b/fill input-field test-content)
          (wait-and-click [:do-not-calls {:tag :button, :fn/has-string "Save"}])
          (b/wait-invisible input-field)
          (b/wait-has-text :do-not-calls test-content))))))


(deftest error-pages-test
  (with-fixtures [with-browser with-per-test-postmortem]
    (doto *driver*
      (b/go *base-url*)
      (do-dev-login)) ; login to avoid 401 Unauthorized (or redirect to login) when testing for 403 Forbidden

    (testing "404 Not Found"
      (doto *driver*
        (b/go (str *base-url* "/foo"))
        (b/wait-visible h1))
      (is (= "Page not found 😵"
             (b/get-element-text *driver* h1))))

    (testing "403 Forbidden"
      (doto *driver*
        (b/go @*congregation-url)
        (b/wait-visible h1))
      (is (= "Access denied 🛑"
             (b/get-element-text *driver* h1))))

    (testing "error pages show the user's authentication status"
      (is (b/visible? *driver* :logout-button)))))


(deftest sudo-test
  (with-fixtures [with-browser with-per-test-postmortem]
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
             (b/get-element-text *driver* h1))))

    (testing "after sudo, can access every congregation"
      (go-to-congregation *driver* *congregation-name*))))
