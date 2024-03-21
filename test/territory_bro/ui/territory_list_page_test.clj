;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.api :as api]
            [territory-bro.api-test :as at]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-list-page :as territory-list-page])
  (:import (java.util UUID)))

(def model
  {:territories [{:id (UUID. 0 1)
                  :number "123"
                  :addresses "the addresses"
                  :region "the region"
                  :meta {:foo "bar"}
                  :location testdata/wkt-multi-polygon}]
   :permissions {:configureCongregation true
                 :editDoNotCalls true
                 :gisAccess true
                 :shareTerritoryLink true
                 :viewCongregation true}})
(def anonymous-model
  (assoc model :permissions {}))

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "foo")
          territory-id (at/create-territory! cong-id)
          request {:params {:congregation (str cong-id)
                            :territory (str territory-id)}
                   :session {::auth/user {:user/id user-id}}}]
      (testing "default"
        (is (= (-> model
                   (replace-in [:territories 0 :id] (UUID. 0 1) territory-id))
               (territory-list-page/model! request))))

      (testing "anonymous user, has opened a share"
        (at/create-share! cong-id territory-id "share123")
        (let [{:keys [session]} (api/open-share {:params {:share-key "share123"}})
              request (assoc request :session session)]
          (is (= (-> anonymous-model
                     (replace-in [:territories 0 :id] (UUID. 0 1) territory-id))
                 (territory-list-page/model! request))))))))

(deftest view-test
  (testing "default"
    (is (= (html/normalize-whitespace
            "Territories

             Search Clear
             Number   Region       Addresses
             123      the region   the addresses")
           (-> (territory-list-page/view model)
               html/visible-text))))

  (testing "each row embeds the searchable text in lowercase"
    (let [model (-> model
                    (replace-in [:territories 0 :number] "123" "123A")
                    (replace-in [:territories 0 :region] "the region" "Some Region")
                    ;; addresses are commonly multiline
                    (replace-in [:territories 0 :addresses] "the addresses" "Some Street\nAnother Street\n"))]
      ;; newline is used as the separator, so that you could not accidentally search from two
      ;; adjacent fields at the same time (one does not simply type a newline to a search field)
      (is (str/includes? (str (territory-list-page/view model))
                         "<tr data-searchable=\"123a\nsome region\nsome street\nanother street\">"))))

  (testing "missing territory number: shows a placeholder so that the link can be clicked"
    (is (= (html/normalize-whitespace
            "Territories

             Search Clear
             Number   Region       Addresses
             -        the region   the addresses")
           (-> (territory-list-page/view (replace-in model [:territories 0 :number] "123" ""))
               html/visible-text))))

  (testing "territory numbers are sorted naturally"
    (let [territories (shuffle [{:number ""}
                                {:number nil} ; nil should not crash, but be treated same as ""
                                {:number "1"}
                                {:number "2"} ; basic string sort would put this after "10"
                                {:number "10"}
                                {:number "10A"}
                                {:number "10b"} ; sorting should be case-insensitive
                                {:number "10C"}])]
      (is (= (html/normalize-whitespace
              "Territories

               Search Clear
               Number   Region       Addresses
               -
               -
               1
               2
               10
               10A
               10b
               10C")
             (-> (territory-list-page/view (assoc model :territories territories))
                 html/visible-text)))))

  (testing "anonymous user, has opened a share"
    (is (= (html/normalize-whitespace
            "Territories

             {fa-info-circle} Why so few territories?
             Only those territories which have been shared with you are currently shown.
             You will need to login to see the rest.

             Search Clear
             Number   Region       Addresses
             123      the region   the addresses")
           (-> (territory-list-page/view anonymous-model)
               html/visible-text))))

  (testing "logged-in user without congregation access, has opened a share"
    (is (= (html/normalize-whitespace
            "Territories

             {fa-info-circle} Why so few territories?
             Only those territories which have been shared with you are currently shown.
             You will need to request access to see the rest.

             Search Clear
             Number   Region       Addresses
             123      the region   the addresses")
           (binding [auth/*user* {:user/id (UUID/randomUUID)}]
             (-> (territory-list-page/view anonymous-model)
                 html/visible-text))))))
