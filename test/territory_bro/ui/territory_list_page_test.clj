;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [reitit.core :as reitit]
            [territory-bro.api :as api]
            [territory-bro.api-test :as at]
            [territory-bro.domain.loan :as loan]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui :as ui]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-list-page :as territory-list-page]
            [territory-bro.ui.territory-page :as territory-page])
  (:import (java.util UUID)))

(def model
  {:congregation-boundary (str (geometry/parse-wkt testdata/wkt-helsinki))
   :territories [{:id (UUID. 0 1)
                  :number "123"
                  :addresses "the addresses"
                  :region "the region"
                  :meta {:foo "bar"}
                  :location testdata/wkt-helsinki-rautatientori}]
   :has-loans? false
   :permissions {:configureCongregation true
                 :editDoNotCalls true
                 :gisAccess true
                 :shareTerritoryLink true
                 :viewCongregation true}})
(def model-loans-enabled
  (replace-in model [:has-loans?] false true))
(def model-loans-fetched
  (update-in model-loans-enabled [:territories 0] merge {:loaned true
                                                         :staleness 7}))
(def anonymous-model
  (assoc model
         :congregation-boundary ""
         :permissions {}))

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "foo")
          _ (at/create-congregation-boundary! cong-id)
          territory-id (at/create-territory! cong-id)
          request {:params {:congregation (str cong-id)
                            :territory (str territory-id)}
                   :session {::auth/user {:user/id user-id}}}
          fix #(replace-in % [:territories 0 :id] (UUID. 0 1) territory-id)]

      (testing "default"
        (is (= (fix model)
               (territory-list-page/model! request {}))))

      (testing "anonymous user, has opened a share"
        (at/create-share! cong-id territory-id "share123")
        (let [{:keys [session]} (api/open-share {:params {:share-key "share123"}})
              request (assoc request :session session)]
          (is (= (fix anonymous-model)
                 (territory-list-page/model! request {})))))

      (testing "loans enabled,"
        (at/change-congregation-settings! cong-id "foo" "https://docs.google.com/example")
        (binding [loan/download! (constantly (str "Number,Loaned,Staleness\n"
                                                  "123,TRUE,7\n"))]
          (testing "not fetched"
            (is (= (fix model-loans-enabled)
                   (territory-list-page/model! request {:fetch-loans? false}))))

          (testing "fetched"
            (is (= (fix model-loans-fetched)
                   (territory-list-page/model! request {:fetch-loans? true})))))))))

(deftest view-test
  (testing "default"
    (is (= (html/normalize-whitespace
            "Territories

             Search [] Clear
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
                         "data-searchable=\"123a\nsome region\nsome street\nanother street\""))
      (is (str/includes? (str (territory-list-page/view model))
                         "data-territory-id=\"00000000-0000-0000-0000-000000000001\""))))

  (testing "missing territory number: shows a placeholder so that the link can be clicked"
    (is (= (html/normalize-whitespace
            "Territories

             Search [] Clear
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

               Search [] Clear
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

  (testing "territory-list-map's JSON data is guarded against XSS"
    ;; If we used a <script type="application/json"> element, we would need to
    ;; guard against "</script>" strings. By using a <template> element, we can
    ;; rely on basic HTML encoding, which Hiccup does automatically.
    (is (str/includes? (str (territory-list-page/view (replace-in model [:territories 0 :number] "123" "</script>")))
                       "&quot;number&quot;:&quot;&lt;/script&gt;&quot;")))

  (testing "loans lazy loading:"
    ;; Loan data is currently loaded from Google Sheets, which can easily take a couple of seconds.
    ;; Lazy loading is needed to load the territory list page faster and defer rendering the map.
    (let [map-html "<territory-list-map"
          placeholder-icon "{fa-map-location-dot}"]
      (testing "loans disabled -> show map immediately"
        (let [rendered (territory-list-page/view model)]
          (is (str/includes? rendered map-html))
          (is (not (str/includes? (html/visible-text rendered) placeholder-icon)))))

      (testing "loans enabled -> show a placeholder and lazy load the map"
        (let [rendered (territory-list-page/view model-loans-enabled)]
          (is (not (str/includes? rendered map-html)))
          (is (str/includes? (html/visible-text rendered) placeholder-icon))))))

  (testing "limited visibility disclaimer:"
    (testing "anonymous user, has opened a share"
      (is (= (html/normalize-whitespace
              "Territories

               {fa-info-circle} Why so few territories?
               Only those territories which have been shared with you are currently shown.
               You will need to login to see the rest.

               Search [] Clear
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

               Search [] Clear
               Number   Region       Addresses
               123      the region   the addresses")
             (binding [auth/*user* {:user/id (UUID/randomUUID)}]
               (-> (territory-list-page/view anonymous-model)
                   html/visible-text)))))))

(deftest route-conflicts-test
  (let [router (reitit/router ui/routes)
        uuid (str (UUID/randomUUID))]
    (testing "htmx component URLs shouldn't conflict with territory page URLs"
      (is (= ::territory-list-page/map
             (-> (reitit/match-by-path router (str "/congregation/" uuid "/territories/map"))
                 :data :name)))
      (is (= ::territory-page/page
             (-> (reitit/match-by-path router (str "/congregation/" uuid "/territories/" uuid))
                 :data :name))))))

