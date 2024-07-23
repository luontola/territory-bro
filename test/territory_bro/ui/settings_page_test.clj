;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.settings-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.api :as api]
            [territory-bro.api-test :as at]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.settings-page :as settings-page])
  (:import (clojure.lang ExceptionInfo)
           (java.util UUID)
           (territory_bro ValidationException)))

(def model
  {:congregation/name "Congregation Name"
   :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"
   :congregation/users [{:id (UUID. 0 1)
                         :name "Esko Luontola"
                         :nickname "esko.luontola"
                         :picture "https://lh6.googleusercontent.com/-AmDv-VVhQBU/AAAAAAAAAAI/AAAAAAAAAeI/bHP8lVNY1aA/photo.jpg"
                         :sub "google-oauth2|102883237794451111459"
                         :new? false}]
   :congregation/permissions {:configure-congregation true
                              :gis-access true}
   :form/user-id nil})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "Congregation Name")
          request {:params {:congregation (str cong-id)}
                   :session (auth/user-session {:name "John Doe"} user-id)}
          model (replace-in model [:congregation/users 0 :id] (UUID. 0 1) user-id)]
      (at/change-congregation-settings! cong-id "Congregation Name" "https://docs.google.com/spreadsheets/123")

      (testing "logged in"
        (is (= model (settings-page/model! request))))

      (testing "demo congregation"
        (binding [config/env (replace-in config/env [:demo-congregation] nil cong-id)]
          (let [request {:params {:congregation "demo"}}]
            (is (thrown-match? ExceptionInfo
                               {:type :ring.util.http-response/response
                                :response {:status 404
                                           :body "Not available in demo"
                                           :headers {}}}
                               (settings-page/model! request))))))

      (testing "anonymous user"
        (is (thrown-match? ExceptionInfo
                           {:type :ring.util.http-response/response
                            :response {:status 401
                                       :body "Not logged in"
                                       :headers {}}}
                           (settings-page/model! (dissoc request :session)))))

      (testing "user was added"
        (let [request (assoc-in request [:params :new-user] (str user-id))
              model (replace-in model [:congregation/users 0 :new?] false true)]
          (is (= model (settings-page/model! request)))))

      (testing "shows new users first, followed by the rest alphabetically"
        (let [new-user-id (UUID. 0 5)
              users [;; should be case-insensitive
                     {:id (UUID. 0 1), :name "a"}
                     {:id (UUID. 0 2), :name "B"}
                     ;; new ones should be first
                     {:id new-user-id, :name "c"}
                     ;; doesn't sort by ID
                     {:id (UUID. 0 9), :name "D"}
                     {:id (UUID. 0 8), :name "e"}]]
          (binding [api/get-congregation (constantly {:body {:congregation/users (shuffle users)}})]
            (is (= ["c" "a" "B" "D" "e"]
                   (->> (settings-page/model! (assoc-in request [:params :new-user] (str new-user-id)))
                        :congregation/users
                        (map :name))))))))))

(deftest congregation-settings-section-test
  (testing "requires the configure-congregation permission"
    (let [model (replace-in model [:congregation/permissions :configure-congregation] true false)]
      (is (nil? (settings-page/congregation-settings-section model))))))

(deftest editing-maps-section-test
  (testing "requires the gis-access permission"
    (let [model (replace-in model [:congregation/permissions :gis-access] true false)]
      (is (nil? (settings-page/editing-maps-section model))))))


(deftest identity-provider-test
  (is (= "" (settings-page/identity-provider nil)))
  (is (= "Google" (settings-page/identity-provider {:sub "google-oauth2|123456789"})))
  (is (= "Facebook" (settings-page/identity-provider {:sub "facebook|10224970701722883"})))
  (is (= "developer" (settings-page/identity-provider {:sub "developer"}))))

(deftest users-table-row-test
  (let [user-id (UUID. 0 1)
        current-user-id (UUID. 0 2)
        user {:id user-id
              :name "John Doe"
              :picture "http://example.com/picture.jpg"
              :email "john.doe@example.com"
              :emailVerified true
              :sub "google-oauth2|123456789"
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
        (let [current-user (assoc user :id current-user-id)]
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
               (-> (settings-page/users-table-row (assoc user :emailVerified false) model)
                   html/visible-text))))

      (testing "user data missing, show ID as placeholder"
        (is (= (html/normalize-whitespace
                "00000000-0000-0000-0000-000000000001   Remove user")
               (-> (settings-page/users-table-row {:id user-id} model)
                   html/visible-text)))))))

(deftest user-management-section-test
  (testing "requires the configure-congregation permission"
    (let [model (replace-in model [:congregation/permissions :configure-congregation] true false)]
      (is (nil? (settings-page/user-management-section model))))))


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

          Users

          To add users to this congregation, ask them to visit https://beta.territorybro.com/join and copy their User ID
          from that page and send it to you.

          User ID []
          Add user

          Name              Email       Login method   Actions
          Esko Luontola                 Google         Remove user")
         (binding [config/env {:public-url "https://beta.territorybro.com"}]
           (-> (settings-page/view model)
               html/visible-text)))))

(deftest ^:slow save-congregation-settings!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "foo")
          request {:params {:congregation (str cong-id)
                            :congregation-name "new name"
                            :loans-csv-url "new url"}
                   :session (auth/user-session {:name "John Doe"} user-id)}]
      (binding [html/*page-path* "/settings-page-url"]

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

(deftest ^:slow add-user!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          new-user-id (UUID. 0 2)
          cong-id (at/create-congregation! session "foo")
          request {:params {:congregation (str cong-id)
                            :user-id (str new-user-id)}
                   :session (auth/user-session {:name "John Doe"} user-id)}]
      (binding [html/*page-path* "/settings-page-url"]

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

(deftest ^:slow remove-user!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          current-user-id (at/get-user-id session)
          other-user-id (UUID. 0 2)
          cong-id (at/create-congregation! session "foo")
          request {:params {:congregation (str cong-id)
                            :user-id (str other-user-id)}
                   :session (auth/user-session {:name "John Doe"} current-user-id)}]
      (binding [html/*page-path* "/settings-page-url"]
        (auth/with-user-from-session request

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
              (let [response (settings-page/remove-user! (assoc-in request [:params :user-id] (str current-user-id)))]
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
                       (dissoc @*last-command :command/time)))))))))))
