;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.settings-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.settings-page :as settings-page])
  (:import (clojure.lang ExceptionInfo)))

(def model {})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          request {:session (auth/user-session {:name "John Doe"} user-id)}]

      (testing "logged in"
        (is (= model (settings-page/model! request))))

      (testing "anonymous user"
        (is (thrown-match? ExceptionInfo
                           {:type :ring.util.http-response/response
                            :response {:status 401
                                       :body "Not logged in"
                                       :headers {}}}
                           (settings-page/model! (dissoc request :session))))))))

(deftest view-test
  (is (= (html/normalize-whitespace
          "Settings

          Congregation name []

          ☑ Experimental features

          Territory loans CSV URL (optional) []
             
            {fa-info-circle} Early Access Feature: Integrate with territory loans data from Google Sheets

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

          To add users to this congregation, ask them to visit http://localhost:8080/join and copy their User ID
          from that page and send it to you.

          User ID []
          Add user

              Name              Email                                Login method   Actions
          👨‍💼  Developer (You)   developer@example.com (Unverified)   developer      Remove user")
         (-> (settings-page/view model)
             html/visible-text))))
