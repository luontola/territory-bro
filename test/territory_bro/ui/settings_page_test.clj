(ns territory-bro.ui.settings-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [medley.core :refer [dissoc-in]]
            [reitit.ring :as ring]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.domain.testdata :as testdata]
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
           (java.time LocalDate)
           (java.util UUID)
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
(def test-publisher-with-assignments
  (assoc test-publisher :assigned-territories [{:territory/id territory-id
                                                :territory/number "123"}]))

(def model
  {:congregation {:congregation/id cong-id
                  :congregation/name "Congregation Name"}
   :publisher nil
   :publishers [test-publisher-with-assignments]
   :users [{:user/id user-id
            :user/subject "google-oauth2|102883237794451111459"
            :user/attributes {:name "Esko Luontola"
                              :nickname "esko.luontola"
                              :picture "https://lh6.googleusercontent.com/-AmDv-VVhQBU/AAAAAAAAAAI/AAAAAAAAAeI/bHP8lVNY1aA/photo.jpg"}
            :new? false}]
   :permissions {:configure-congregation true
                 :gis-access true}
   :form {:congregation-name "Congregation Name"
          :loans-csv-url "https://docs.google.com/spreadsheets/123"
          :publisher-id nil
          :publisher-name ""
          :user-id nil}})
(def publisher-model
  (-> model
      (assoc :publisher test-publisher-with-assignments)
      (update :form merge {:publisher-id publisher-id
                           :publisher-name "John Doe"})))

(def test-events
  (flatten [{:event/type :congregation.event/congregation-created
             :congregation/id cong-id
             :congregation/name "Congregation Name"
             :congregation/schema-name "cong1_schema"}
            (congregation/admin-permissions-granted cong-id user-id)
            {:event/type :congregation.event/settings-updated
             :congregation/id cong-id
             :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"}
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

(use-fixtures :once (fn [f]
                      (binding [html/*page-path* "/settings-page-url"
                                user/get-users fake-get-users
                                publisher/publishers-by-id (fn [_conn cong-id]
                                                             (get test-publishers-by-id cong-id))]
                        (f))))

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
          (binding [config/env {:demo-congregation cong-id}]
            (let [request {:path-params {:congregation "demo"}}]
              (is (thrown-match? ExceptionInfo dmz-test/access-denied
                                 (settings-page/model! request))))))

        (testing "anonymous"
          (testutil/with-anonymous-user
            (is (thrown-match? ExceptionInfo dmz-test/not-logged-in
                               (settings-page/model! request)))))

        (testing "editing a publisher"
          (let [request (assoc-in request [:path-params :publisher] publisher-id)]
            (is (= publisher-model (settings-page/model! request)))))

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

(deftest congregation-settings-section-test
  (testing "requires the configure-congregation permission"
    (let [model (replace-in model [:permissions :configure-congregation] true false)]
      (is (nil? (settings-page/congregation-settings-section model))))))

(deftest save-congregation-settings!-test
  (let [request {:path-params {:congregation cong-id}
                 :params {:congregation-name "new name"
                          :loans-csv-url "new url"}}]
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
                      :congregation/loans-csv-url "new url"
                      :congregation/name "new name"}
                     (dissoc @*last-command :command/time))))))

        (testing "save failed: highlights erroneous form fields, doesn't forget invalid user input"
          (binding [dispatcher/command! (fn [& _]
                                          (throw (ValidationException. [[:missing-name]
                                                                        [:disallowed-loans-csv-url]])))]
            (let [response (settings-page/save-congregation-settings! request)]
              (is (= forms/validation-error-http-status (:status response)))
              (is (str/includes?
                   (html/visible-text (:body response))
                   (html/normalize-whitespace
                    "Congregation name [new name] ⚠️
                     Experimental features
                     Territory loans CSV URL (optional) [new url] ⚠️"))))))))))


;;;; Editing maps

(deftest editing-maps-section-test
  (testing "requires the gis-access permission"
    (let [model (replace-in model [:permissions :gis-access] true false)]
      (is (nil? (settings-page/editing-maps-section model))))))

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
                                "Content-Disposition" "attachment; filename=\"Congregation Name.qgs\""}}
                     (dissoc response :body)))
              (is (str/includes? (:body response) "dbname='example_db' host=gis.example.com port=5432 user='username123'")))))))))


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
    (let [model (dissoc-in publisher-model [:publisher :assigned-territories])]
      (is (= (html/normalize-whitespace
              "John Doe   Edit")
             (-> (settings-page/view-publisher-row model)
                 html/visible-text)))))

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
                              "<tr class=\"UserManagement__newUser--")
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

          Experimental features

          Territory loans CSV URL (optional) [https://docs.google.com/spreadsheets/123] Link to a Google Sheets spreadsheet published to the web as CSV
             
            {info.svg} Early Access Feature: Integrate with territory loans data from Google Sheets

            If you keep track of your territory loans using Google Sheets, it's possible to export the data from there
            and visualize it on the map on Territory Bro's Territories page. Eventually Territory Bro will handle the
            territory loans accounting all by itself, but in the meanwhile this workaround gives some of the benefits.

            Here is an example spreadsheet that you can use as a starting point. Also please contact me for assistance
            and so that I will know to help you later with migration to full accounting support.

            You'll need to create a sheet with the following structure:

            Number Loaned Staleness
            101    TRUE   2
            102    FALSE  6

            The Number column should contain the territory number. It's should match the territories in Territory Bro.

            The Loaned column should contain \"TRUE\" when the territory is currently loaned to a publisher and
            \"FALSE\" when nobody has it.

            The Staleness column should indicate the number of months since the territory was last loaned or returned.

            The first row of the sheet must contain the column names, but otherwise the sheet's structure is flexible:
            The columns can be in any order. Columns with other names are ignored. Empty rows are ignored.

            After you have such a sheet, you can expose it to the Internet through File | Share | Publish to web.

            Publish that sheet as a CSV file and enter its URL to the above field on this settings page.

          Save settings

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
                                       :uri (str page-path "/users")}))))))))
