(ns territory-bro.ui.settings-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [medley.core :as m]
            [reitit.ring :as ring]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.do-not-calls-test :as do-not-calls-test]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.qgis :as qgis]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.infra.user :as user]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.settings-page :as settings-page])
  (:import (clojure.lang ExceptionInfo)
           (java.io InputStream)
           (java.time LocalDate LocalTime OffsetDateTime ZoneOffset)
           (java.util UUID)
           (org.apache.poi.ss.usermodel Sheet)
           (org.apache.poi.xssf.usermodel XSSFWorkbook)
           (territory_bro ValidationException)))

(def cong-id (UUID. 0 1))
(def territory-id (UUID. 0 2))
(def assignment-id (UUID. 0 3))
(def publisher-id (UUID. 0 4))
(def user-id (UUID. 0 5))
(def test-publisher
  {:congregation/id cong-id
   :publisher/id publisher-id
   :publisher/name "John Doe"})
(def enriched-publisher
  (-> test-publisher
      (select-keys [:publisher/id :publisher/name])
      (assoc :assigned-territories [{:territory/id territory-id
                                     :territory/number "123"}])
      (assoc :new? false)))

(def model
  {:congregation {:congregation/id cong-id
                  :congregation/name "Congregation Name"
                  :congregation/timezone ZoneOffset/UTC}
   :publisher nil
   :publishers [enriched-publisher]
   :users [{:user/id user-id
            :user/subject "google-oauth2|102883237794451111459"
            :user/attributes {:name "Esko Luontola"
                              :nickname "esko.luontola"
                              :picture "https://lh6.googleusercontent.com/-AmDv-VVhQBU/AAAAAAAAAAI/AAAAAAAAAeI/bHP8lVNY1aA/photo.jpg"}
            :new? false}]
   :permissions {:configure-congregation true
                 :gis-access true}
   :form {:congregation-name "Congregation Name"
          :expire-shared-links-on-return true
          :publisher-name ""
          :user-id nil}})
(def publisher-model
  (-> model
      (replace-in [:publisher] nil enriched-publisher)
      (replace-in [:form :publisher-name] "" "John Doe")))

(def test-events
  (flatten [{:event/type :congregation.event/congregation-created
             :congregation/id cong-id
             :congregation/name "Congregation Name"
             :congregation/schema-name "cong1_schema"}
            (congregation/admin-permissions-granted cong-id user-id)
            {:event/type :territory.event/territory-defined
             :congregation/id cong-id
             :territory/id territory-id
             :territory/number "123"
             :territory/addresses "the addresses"
             :territory/region "the region"
             :territory/meta {:foo "bar"}
             :territory/location testdata/wkt-helsinki-rautatientori}
            {:event/type :territory.event/territory-assigned
             :congregation/id cong-id
             :territory/id territory-id
             :assignment/id assignment-id
             :assignment/start-date (LocalDate/of 2000 1 1)
             :publisher/id publisher-id}]))

(def test-publishers-by-id {cong-id {publisher-id test-publisher}})

(def fake-get-users
  (fn [_conn query]
    (is (= {:ids [user-id]} query)
        "get-users query")
    [{:user/id user-id
      :user/subject "google-oauth2|102883237794451111459"
      :user/attributes {:name "Esko Luontola"
                        :nickname "esko.luontola"
                        :picture "https://lh6.googleusercontent.com/-AmDv-VVhQBU/AAAAAAAAAAI/AAAAAAAAAeI/bHP8lVNY1aA/photo.jpg"}}]))

(def test-time (.toInstant (OffsetDateTime/of (LocalDate/of 2000 1 30) LocalTime/NOON ZoneOffset/UTC)))

(use-fixtures :once (join-fixtures [(fixed-clock-fixture test-time)
                                    (fn [f]
                                      (binding [html/*page-path* "/settings-page-url"
                                                user/get-users fake-get-users
                                                publisher/publishers-by-id (fn [_conn cong-id]
                                                                             (get test-publishers-by-id cong-id))]
                                        (f)))]))

(deftest model!-test
  (let [request {:path-params {:congregation cong-id}}]
    (testutil/with-events test-events
      (testutil/with-user-id user-id

        (testing "logged in"
          (is (= model (settings-page/model! request))))

        (testing "only view permissions, not an admin"
          (binding [dmz/*state* (-> dmz/*state*
                                    (permissions/revoke user-id [:configure-congregation cong-id])
                                    (permissions/revoke user-id [:gis-access cong-id]))]
            (is (thrown-match? ExceptionInfo dmz-test/access-denied
                               (settings-page/model! request)))))

        (testing "demo congregation"
          (let [request {:path-params {:congregation "demo"}}]
            (is (thrown-match? ExceptionInfo dmz-test/access-denied
                               (settings-page/model! request)))))

        (testing "anonymous"
          (testutil/with-anonymous-user
            (is (thrown-match? ExceptionInfo dmz-test/not-logged-in
                               (settings-page/model! request)))))

        (testing "editing a publisher"
          (let [request (assoc-in request [:path-params :publisher] publisher-id)]
            (is (= publisher-model (settings-page/model! request)))))

        (testing "publisher was added"
          (let [request (assoc-in request [:params :new-publisher] (str publisher-id))
                model (replace-in model [:publishers 0 :new?] false true)]
            (is (= model (settings-page/model! request)))))

        (testing "user was added"
          (let [request (assoc-in request [:params :new-user] (str user-id))
                model (replace-in model [:users 0 :new?] false true)]
            (is (= model (settings-page/model! request)))))

        (testing "shows new users first, followed by the rest alphabetically"
          (let [new-user-id (UUID. 0 5)
                request (assoc-in request [:params :new-user] (str new-user-id))
                users [;; should be case-insensitive
                       {:user/id (UUID. 0 1), :user/attributes {:name "a"}}
                       {:user/id (UUID. 0 2), :user/attributes {:name "B"}}
                       ;; new ones should be first
                       {:user/id new-user-id, :user/attributes {:name "c"}}
                       ;; doesn't sort by ID
                       {:user/id (UUID. 0 9), :user/attributes {:name "D"}}
                       {:user/id (UUID. 0 8), :user/attributes {:name "e"}}]]
            (binding [user/get-users (constantly (shuffle users))]
              (is (= ["c" "a" "B" "D" "e"]
                     (->> (settings-page/model! request)
                          :users
                          (mapv (comp :name :user/attributes))))))))))))

;;;; Congregation settings

(deftest congregation-timezone-field-test
  (testing "timezone is not set"
    (is (= "Timezone UTC (2000-01-30 12:00) Define congregation boundaries to set the timezone"
           (-> (settings-page/congregation-timezone-field model)
               html/visible-text))))

  (testing "timezone is set"
    (let [model (replace-in model [:congregation :congregation/timezone] ZoneOffset/UTC testdata/timezone-helsinki)]
      (is (= "Timezone Europe/Helsinki (2000-01-30 14:00) Autodetected based on the congregation boundaries"
             (-> (settings-page/congregation-timezone-field model)
                 html/visible-text))))))

(deftest congregation-settings-section-test
  (testing "requires the configure-congregation permission"
    (let [model (replace-in model [:permissions :configure-congregation] true false)]
      (is (nil? (settings-page/congregation-settings-section model))))))

(deftest save-congregation-settings!-test
  (let [request {:path-params {:congregation cong-id}
                 :params {:congregation-name "new name"
                          :expire-shared-links-on-return "true"}}]
    (testutil/with-events test-events
      (testutil/with-user-id user-id

        (testing "save successful: redirects to same page"
          (with-fixtures [fake-dispatcher-fixture]
            (let [response (settings-page/save-congregation-settings! request)]
              (is (= {:status 303
                      :headers {"Location" "/settings-page-url"}
                      :body ""}
                     response))
              (is (= {:command/type :congregation.command/update-congregation
                      :command/user user-id
                      :congregation/id cong-id
                      :congregation/name "new name"
                      :congregation/expire-shared-links-on-return true}
                     (dissoc @*last-command :command/time))))))

        (testing "save failed: highlights erroneous form fields, doesn't forget invalid user input"
          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:missing-name]])))]
            (let [response (settings-page/save-congregation-settings! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "Congregation name [new name] ⚠️
                     Timezone UTC (2000-01-30 12:00) Define congregation boundaries to set the timezone"))))))))))


;;;; Territories

(deftest editing-maps-section-test
  (testing "requires the gis-access permission"
    (let [model (replace-in model [:permissions :gis-access] true false)]
      (is (nil? (settings-page/editing-maps-section model))))))

(deftest territories-section-test
  (testing "requires the gis-access or configure-congregation permission"
    (let [model (-> model
                    (replace-in [:permissions :gis-access] true false)
                    (replace-in [:permissions :configure-congregation] true false))]
      (is (nil? (settings-page/territories-section model)))))

  (testing "only gis-access permission: will show the editing maps section"
    (let [model (replace-in model [:permissions :configure-congregation] true false)]
      (is (= (html/normalize-whitespace
              "Territories
               Editing maps
               The instructions for editing maps are in the user guide. You can edit the maps using the QGIS application,
               for which you will need the following QGIS project file.
               Download QGIS project file")
             (-> (settings-page/territories-section model)
                 html/visible-text)))))

  (testing "only configure-congregation permission: will show the export territories link"
    (let [model (replace-in model [:permissions :gis-access] true false)]
      (is (= (html/normalize-whitespace
              "Territories
               Export territories and assignments as a spreadsheet (.xlsx)")
             (-> (settings-page/territories-section model)
                 html/visible-text))))))

(deftest sanitize-filename-test
  (testing "joins the basename and extension"
    (is (= "hello.txt" (settings-page/sanitize-filename "hello" ".txt"))))

  (testing "keeps non-ASCII characters"
    (is (= "Ylöjärvi.qgs" (settings-page/sanitize-filename "Ylöjärvi" qgis/qgis-project-ext)))
    (is (= "東京.qgs" (settings-page/sanitize-filename "東京" qgis/qgis-project-ext))))

  (testing "strips illegal characters"
    ;; https://stackoverflow.com/a/31976060/62130
    (is (= "foobar.zip"
           (settings-page/sanitize-filename "foo<>:\"/\\|?*bar" ".zip"))
        "forbidden printable ASCII characters")
    (is (= "foo.zip"
           (settings-page/sanitize-filename ".foo" ".zip")
           (settings-page/sanitize-filename "..foo" ".zip"))
        "leading commas (i.e. hidden file)")
    (is (= "a.b..zip"
           (settings-page/sanitize-filename "a.b." ".zip"))
        "trailing commas are kept"))

  (testing "normalizes whitespace"
    (is (= "foo bar.txt" (settings-page/sanitize-filename "  foo   \tbar \n" ".txt")))
    (is (= "foo.txt" (settings-page/sanitize-filename " . . . foo" ".txt"))
        "trimming whitespace should not create leading commands"))

  (testing "uses a fallback if the basename is empty"
    (is (= "file.txt"
           (settings-page/sanitize-filename "" ".txt")
           (settings-page/sanitize-filename " " ".txt")
           (settings-page/sanitize-filename "/" ".txt")))))

(deftest content-disposition-test
  (testing "encoding space"
    (is (= "attachment; filename=\"file name.txt\"; filename*=UTF-8''file%20name.txt"
           (settings-page/content-disposition "file name.txt"))))

  (testing "encoding +"
    (is (= "attachment; filename=\"a+b.txt\"; filename*=UTF-8''a%2Bb.txt"
           (settings-page/content-disposition "a+b.txt"))))

  (testing "ASCII fallback by removing diacritics"
    (is (= "attachment; filename=\"Ylojarvi.txt\"; filename*=UTF-8''Yl%C3%B6j%C3%A4rvi.txt"
           (settings-page/content-disposition "Ylöjärvi.txt"))))

  (testing "ASCII fallback for non-representable characters"
    (is (= "attachment; filename=\"__.txt\"; filename*=UTF-8''%E6%9D%B1%E4%BA%AC.txt"
           (settings-page/content-disposition "東京.txt")))))

(deftest download-qgis-project-test
  (let [request {:path-params {:congregation cong-id}}]
    (testutil/with-events (concat test-events
                                  [{:event/type :congregation.event/gis-user-created
                                    :congregation/id cong-id
                                    :user/id user-id
                                    :gis-user/username "username123"
                                    :gis-user/password "password123"}])
      (binding [config/env {:gis-database-host "gis.example.com"
                            :gis-database-name "example_db"}]
        (testutil/with-user-id user-id

          (testing "downloads the QGIS project file for the congregation and user"
            (let [response (settings-page/download-qgis-project request)]
              (is (= {:status 200
                      :headers {"Content-Type" "application/octet-stream"
                                "Content-Disposition" "attachment; filename=\"Congregation Name.qgs\"; filename*=UTF-8''Congregation%20Name.qgs"}}
                     (dissoc response :body)))
              (is (str/includes? (:body response) "dbname='example_db' host=gis.example.com port=5432 user='username123'")))))))))

(deftest export-territories-test
  (let [request {:path-params {:congregation cong-id}}]
    (testutil/with-events test-events
      (binding [do-not-calls/get-do-not-calls do-not-calls-test/fake-get-do-not-calls]
        (testutil/with-user-id user-id

          (testing "downloads an Excel spreadsheet with territories and assignments"
            (let [response (settings-page/export-territories request)]
              (is (= {:status 200
                      :headers {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                "Content-Disposition" "attachment; filename=\"Congregation Name.xlsx\"; filename*=UTF-8''Congregation%20Name.xlsx"}}
                     (dissoc response :body)))
              (is (= ["Territories" "Assignments"]
                     (->> (XSSFWorkbook. ^InputStream (:body response))
                          .sheetIterator
                          iterator-seq
                          (mapv Sheet/.getSheetName)))))))))))


;;;; Publishers

(deftest view-publisher-row-test
  (testing "shows the publisher and their assigned territories"
    (let [html (settings-page/view-publisher-row publisher-model)]
      (is (= (html/normalize-whitespace
              "John Doe   123   Edit")
             (html/visible-text html)))
      (is (str/includes? html " href=\"/congregation/00000000-0000-0000-0000-000000000001/territories/00000000-0000-0000-0000-000000000002\"")
          "links to the territory")))

  (testing "multiple assigned territories"
    (let [model (update-in publisher-model [:publisher :assigned-territories] concat [{:territory/id (random-uuid)
                                                                                       :territory/number "100"}
                                                                                      {:territory/id (random-uuid)
                                                                                       :territory/number "200"}])]
      (is (= (html/normalize-whitespace
              "John Doe   100, 123, 200   Edit")
             (-> (settings-page/view-publisher-row model)
                 html/visible-text)))))

  (testing "no assigned territories"
    (let [model (m/dissoc-in publisher-model [:publisher :assigned-territories])]
      (is (= (html/normalize-whitespace
              "John Doe   Edit")
             (-> (settings-page/view-publisher-row model)
                 html/visible-text)))))

  (testing "highlights new publishers"
    (let [new-row-class " class=\"SettingsPage__new-row--"]
      (is (not (str/includes? (settings-page/view-publisher-row publisher-model)
                              new-row-class))
          "old publisher")
      (is (str/includes? (settings-page/view-publisher-row (replace-in publisher-model [:publisher :new?] false true))
                         new-row-class)
          "new publisher")))

  (testing "requires the configure-congregation permission"
    (let [model (replace-in publisher-model [:permissions :configure-congregation] true false)]
      (is (nil? (settings-page/view-publisher-row model)))))

  (testing "publisher not found: removes the table row"
    ;; Handles the situation that the publisher has been removed, and the row is refreshed with htmx.
    ;; Empty string removes the table row, whereas nil would return HTTP error 404.
    (let [model (dissoc publisher-model :publisher)]
      (is (= "" (settings-page/view-publisher-row model))))))

(deftest edit-publisher-row-test
  (testing "shows a form for editing the publisher"
    (is (= (html/normalize-whitespace
            "[John Doe]   Save   Delete   Cancel")
           (-> (settings-page/edit-publisher-row publisher-model)
               html/visible-text))))

  (testing "shows a delete confirmation if the user has assigned territories"
    (let [delete-confirmation #" hx-confirm=\"John Doe has assigned territories.*\n\nAre you sure you want to delete John Doe\?\""
          no-assignments-model (m/dissoc-in publisher-model [:publisher :assigned-territories])]
      (is (re-find delete-confirmation (str (settings-page/edit-publisher-row publisher-model)))
          "has assignments")
      (is (not (re-find delete-confirmation (str (settings-page/edit-publisher-row no-assignments-model))))
          "doesn't have assignments")))

  (testing "requires the configure-congregation permission"
    (let [model (replace-in publisher-model [:permissions :configure-congregation] true false)]
      (is (nil? (settings-page/edit-publisher-row model)))))

  (testing "publisher not found: removes the table row"
    ;; Handles the situation that the publisher has been removed, and the row is refreshed with htmx.
    ;; Empty string removes the table row, whereas nil would return HTTP error 404.
    (let [model (dissoc publisher-model :publisher)]
      (is (= "" (settings-page/edit-publisher-row model))))))

(deftest add-publisher-row-test
  (testing "shows a form for adding a new publisher"
    (is (= "[] Add publisher"
           (-> (settings-page/add-publisher-row model)
               html/visible-text)))))

(deftest publisher-management-section-test
  ;; TODO: another permit for publisher management? or only for quick-adding publishers?
  (testing "requires the configure-congregation permission"
    (let [model (replace-in model [:permissions :configure-congregation] true false)]
      (is (nil? (settings-page/publisher-management-section model))))))

(deftest add-publisher!-test
  (let [new-publisher-id (UUID. 0 0x42)
        request {:path-params {:congregation cong-id}
                 :params {:publisher-name "John Doe"}}]
    (testutil/with-events test-events
      (testutil/with-user-id user-id

        (testing "add successful: highlights the added publisher"
          (with-fixtures [fake-dispatcher-fixture]
            (let [response (settings-page/add-publisher! request new-publisher-id)]
              (is (= {:status 303
                      :headers {"Location" "/settings-page-url/publishers?new-publisher=00000000-0000-0000-0000-000000000042"}
                      :body ""}
                     response))
              (is (= {:command/type :publisher.command/add-publisher
                      :command/user user-id
                      :congregation/id cong-id
                      :publisher/id new-publisher-id
                      :publisher/name "John Doe"}
                     (dissoc @*last-command :command/time))))))

        (testing "add failed: highlights erroneous form fields, doesn't forget invalid user input"
          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:missing-name]])))]
            (let [request (assoc-in request [:params :publisher-name] " ")
                  response (settings-page/add-publisher! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "[ ] Add publisher
                     ⚠️ Name is required")))))

          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:non-unique-name]])))]
            (let [response (settings-page/add-publisher! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "[John Doe] Add publisher
                     ⚠️ There is already a publisher with that name"))))))))))

(deftest update-publisher!-test
  (let [request {:path-params {:congregation cong-id
                               :publisher publisher-id}
                 :params {:publisher-name "new name"}}]
    (testutil/with-events test-events
      (testutil/with-user-id user-id

        (testing "update successful"
          (with-fixtures [fake-dispatcher-fixture]
            (let [response (settings-page/update-publisher! request)]
              (is (= {:status 303
                      :headers {"Location" "/settings-page-url/publishers/00000000-0000-0000-0000-000000000004"}
                      :body ""}
                     response))
              (is (= {:command/type :publisher.command/update-publisher
                      :command/user user-id
                      :congregation/id cong-id
                      :publisher/id publisher-id
                      :publisher/name "new name"}
                     (dissoc @*last-command :command/time))))))

        (testing "add failed: highlights erroneous form fields, doesn't forget invalid user input"
          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:missing-name]])))]
            (let [request (assoc-in request [:params :publisher-name] " ")
                  response (settings-page/update-publisher! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "[ ] Save Delete Cancel
                     ⚠️ Name is required")))))

          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:non-unique-name]])))]
            (let [response (settings-page/update-publisher! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "[new name] Save Delete Cancel
                     ⚠️ There is already a publisher with that name"))))))))))

(deftest delete-publisher!-test
  (let [request {:path-params {:congregation cong-id
                               :publisher publisher-id}}]
    (testutil/with-events test-events
      (testutil/with-user-id user-id

        (testing "delete successful"
          (with-fixtures [fake-dispatcher-fixture]
            (let [response (settings-page/delete-publisher! request)]
              (is (= {:status 303
                      :headers {"Location" "/settings-page-url/publishers/00000000-0000-0000-0000-000000000004"}
                      :body ""}
                     response))
              (is (= {:command/type :publisher.command/delete-publisher
                      :command/user user-id
                      :congregation/id cong-id
                      :publisher/id publisher-id}
                     (dissoc @*last-command :command/time))))))))))


;;;; Users

(deftest identity-provider-test
  (is (= "" (settings-page/identity-provider nil)))
  (is (= "Auth0" (settings-page/identity-provider {:user/subject "auth0|500fb0b853ad664d1a50c227"})))
  (is (= "Google" (settings-page/identity-provider {:user/subject "google-oauth2|123456789"})))
  (is (= "Facebook" (settings-page/identity-provider {:user/subject "facebook|10224970701722883"})))
  (is (= "developer" (settings-page/identity-provider {:user/subject "developer"}))))

(deftest users-table-row-test
  (let [user-id (UUID. 0 1)
        current-user-id (UUID. 0 2)
        user {:user/id user-id
              :user/subject "google-oauth2|123456789"
              :user/attributes {:name "John Doe"
                                :picture "http://example.com/picture.jpg"
                                :email "john.doe@example.com"
                                :email_verified true}
              :new? false}
        self-delete-confirmation "hx-confirm=\"Are you sure you want to REMOVE YOURSELF from Congregation Name? You will not be able to add yourself back.\""]
    (binding [auth/*user* {:user/id current-user-id}]

      (testing "shows user information"
        (is (= (html/normalize-whitespace
                "John Doe   john.doe@example.com   Google   Remove user")
               (-> (settings-page/users-table-row user model)
                   html/visible-text)))
        (is (str/includes? (str (settings-page/users-table-row user model))
                           "src=\"http://example.com/picture.jpg\"")
            "profile picture")
        (is (not (str/includes? (str (settings-page/users-table-row user model))
                                self-delete-confirmation))
            "no delete confirmation"))

      (testing "highlights the current user"
        (let [current-user (replace-in user [:user/id] user-id current-user-id)]
          (is (= (html/normalize-whitespace
                  "John Doe (You)   john.doe@example.com   Google   Remove user")
                 (-> (settings-page/users-table-row current-user model)
                     html/visible-text)))
          (is (str/includes? (str (settings-page/users-table-row current-user model))
                             self-delete-confirmation)
              "delete confirmation")))

      (testing "highlights new users"
        (is (str/starts-with? (str (settings-page/users-table-row user model))
                              "<tr>")
            "old user")
        (is (str/starts-with? (str (settings-page/users-table-row (replace-in user [:new?] false true) model))
                              "<tr class=\"SettingsPage__new-row--")
            "new user"))

      (testing "highlights unverified emails"
        (is (= (html/normalize-whitespace
                "John Doe   john.doe@example.com (Unverified)   Google   Remove user")
               (-> (settings-page/users-table-row (replace-in user [:user/attributes :email_verified] true false) model)
                   html/visible-text))))

      (testing "user data missing, show ID as placeholder"
        (is (= (html/normalize-whitespace
                "00000000-0000-0000-0000-000000000001   Remove user")
               (-> (settings-page/users-table-row (select-keys user [:user/id]) model)
                   html/visible-text)))))))

(deftest user-management-section-test
  (testing "requires the configure-congregation permission"
    (let [model (replace-in model [:permissions :configure-congregation] true false)]
      (is (nil? (settings-page/user-management-section model))))))

(deftest add-user!-test
  (let [new-user-id (UUID. 0 2)
        request {:path-params {:congregation cong-id}
                 :params {:user-id (str new-user-id)}}]
    (testutil/with-events test-events
      (testutil/with-user-id user-id

        (testing "add successful: highlights the added user"
          (with-fixtures [fake-dispatcher-fixture]
            (let [response (settings-page/add-user! request)]
              (is (= {:status 303
                      :headers {"Location" "/settings-page-url/users?new-user=00000000-0000-0000-0000-000000000002"}
                      :body ""}
                     response))
              (is (= {:command/type :congregation.command/add-user
                      :command/user user-id
                      :congregation/id cong-id
                      :user/id new-user-id}
                     (dissoc @*last-command :command/time))))))

        (testing "trims the user ID"
          (with-fixtures [fake-dispatcher-fixture]
            (let [request (replace-in request [:params :user-id] (str new-user-id) (str "  " new-user-id "  "))
                  response (settings-page/add-user! request)]
              (is (= {:status 303
                      :headers {"Location" "/settings-page-url/users?new-user=00000000-0000-0000-0000-000000000002"}
                      :body ""}
                     response)))))

        (testing "add failed: highlights erroneous form fields, doesn't forget invalid user input"
          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:no-such-user new-user-id]])))]
            (let [response (settings-page/add-user! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "User ID [00000000-0000-0000-0000-000000000002] ⚠️ User does not exist
                     Add user")))))

          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:invalid-user-id]])))]
            (let [request (replace-in request [:params :user-id] (str new-user-id) "foo")
                  response (settings-page/add-user! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "User ID [foo] ⚠️
                     Add user"))))))))))

(deftest remove-user!-test
  (let [current-user-id user-id
        other-user-id (UUID. 0 2)
        request {:path-params {:congregation cong-id}
                 :params {:user-id (str other-user-id)}}]
    (testutil/with-events test-events
      (testutil/with-user-id current-user-id

        (testing "removes the user and refreshes the users list"
          (with-fixtures [fake-dispatcher-fixture]
            (let [response (settings-page/remove-user! request)]
              (is (= {:status 303
                      :headers {"Location" "/settings-page-url/users"}
                      :body ""}
                     response))
              (is (= {:command/type :congregation.command/set-user-permissions
                      :command/user current-user-id
                      :congregation/id cong-id
                      :user/id other-user-id
                      :permission/ids []}
                     (dissoc @*last-command :command/time))))))

        (testing "removing the current user will redirect to the front page"
          (with-fixtures [fake-dispatcher-fixture]
            (let [request (assoc request :params {:user-id (str current-user-id)})
                  response (settings-page/remove-user! request)]
              (is (= {:status 200
                      :headers {"Content-Type" "text/html"
                                "hx-redirect" "/"}
                      :body ""}
                     response))
              (is (= {:command/type :congregation.command/set-user-permissions
                      :command/user current-user-id
                      :congregation/id cong-id
                      :user/id current-user-id
                      :permission/ids []}
                     (dissoc @*last-command :command/time))))))))))


;;;; Main view

(deftest view-test
  (is (= (html/normalize-whitespace
          "Settings

            Congregation name [Congregation Name]

            Timezone UTC (2000-01-30 12:00) Define congregation boundaries to set the timezone

            [x] Expire shared links when a territory is returned

            Save settings
          
          Territories

            Export territories and assignments as a spreadsheet (.xlsx)

          Editing maps

            The instructions for editing maps are in the user guide.
            You can edit the maps using the QGIS application, for which you will need the following QGIS project file.

            Download QGIS project file

          Publishers

            Name       Assigned territories
            John Doe   123                    Edit
            [] Add publisher

          Users

          To add users to this congregation, ask them to visit https://territorybro.com/join and copy their User ID
          from that page and send it to you.

          User ID []
          Add user

          Name              Email       Login method   Actions
          Esko Luontola                 Google         Remove user")
         (binding [config/env {:public-url "https://territorybro.com"}]
           (-> (settings-page/view model)
               html/visible-text)))))

(deftest routes-test
  (let [handler (ring/ring-handler (ring/router settings-page/routes))
        page-path (str "/congregation/" cong-id "/settings")]
    (testutil/with-events test-events

      (testing "all the routes are guarded by access checks"
        (testutil/with-user-id (UUID. 0 0x666)
          (is (thrown-match? ExceptionInfo dmz-test/access-denied
                             (handler {:request-method :get
                                       :uri page-path})))
          (is (thrown-match? ExceptionInfo dmz-test/access-denied
                             (handler {:request-method :get
                                       :uri (str page-path "/qgis-project")})))
          (is (thrown-match? ExceptionInfo dmz-test/access-denied
                             (handler {:request-method :get
                                       :uri (str page-path "/export-territories")})))
          (is (thrown-match? ExceptionInfo dmz-test/access-denied
                             (handler {:request-method :get
                                       :uri (str page-path "/users")}))))))))
