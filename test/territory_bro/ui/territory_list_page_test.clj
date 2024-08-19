;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-list-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [reitit.core :as reitit]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.loan :as loan]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui :as ui]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-list-page :as territory-list-page]
            [territory-bro.ui.territory-page :as territory-page])
  (:import (java.util UUID)))

(def user-id (UUID/randomUUID))
(def cong-id (UUID/randomUUID))
(def territory-id (UUID. 0 1))
(def model
  {:congregation-boundary testdata/wkt-helsinki
   :territories [{:territory/id territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-helsinki-rautatientori}]
   :has-loans? false
   :permissions {:view-congregation-temporarily false}})
(def model-loans-enabled
  (replace-in model [:has-loans?] false true))
(def model-loans-fetched
  (update-in model-loans-enabled [:territories 0] merge {:territory/loaned? true
                                                         :territory/staleness 7}))
(def demo-model
  (assoc model
         :permissions {:view-congregation-temporarily false}))
(def anonymous-model
  (assoc model
         :congregation-boundary nil
         :permissions {:view-congregation-temporarily true}))

(deftest model!-test
  (let [request {:path-params {:congregation cong-id}}]
    (testutil/with-events (flatten [{:event/type :congregation.event/congregation-created
                                     :congregation/id cong-id
                                     :congregation/name "Congregation 1"
                                     :congregation/schema-name "cong1_schema"}
                                    (congregation/admin-permissions-granted cong-id user-id)
                                    {:event/type :congregation-boundary.event/congregation-boundary-defined
                                     :congregation/id cong-id
                                     :congregation-boundary/id (UUID/randomUUID)
                                     :congregation-boundary/location testdata/wkt-helsinki}
                                    {:event/type :territory.event/territory-defined
                                     :congregation/id cong-id
                                     :territory/id territory-id
                                     :territory/number "123"
                                     :territory/addresses "the addresses"
                                     :territory/region "the region"
                                     :territory/meta {:foo "bar"}
                                     :territory/location testdata/wkt-helsinki-rautatientori}])
      (testutil/with-user-id user-id
        (testing "default"
          (is (= model (territory-list-page/model! request {}))))

        (testing "demo congregation"
          (binding [config/env {:demo-congregation cong-id}]
            (let [request {:path-params {:congregation "demo"}}]
              (is (= demo-model
                     (territory-list-page/model! request {})
                     (testutil/with-anonymous-user
                       (territory-list-page/model! request {})))))))

        (testing "anonymous, has opened a share"
          (testutil/with-anonymous-user
            (let [share-id (UUID/randomUUID)
                  request (assoc request :session {::dmz/opened-shares #{share-id}})]
              (testutil/with-events [{:event/type :share.event/share-created
                                      :share/id share-id
                                      :share/key "share123"
                                      :share/type :link
                                      :congregation/id cong-id
                                      :territory/id territory-id}]
                (testutil/with-request-state request
                  (is (= anonymous-model
                         (territory-list-page/model! request {}))))))))

        (testing "loans enabled,"
          (testutil/with-events [{:event/type :congregation.event/settings-updated
                                  :congregation/id cong-id
                                  :congregation/loans-csv-url "https://docs.google.com/spreadsheets/1"}]
            (binding [loan/download! (constantly (str "Number,Loaned,Staleness\n"
                                                      "123,TRUE,7\n"))]
              (testing "not fetched"
                (is (= model-loans-enabled
                       (territory-list-page/model! request {:fetch-loans? false}))))

              (testing "fetched"
                (is (= model-loans-fetched
                       (territory-list-page/model! request {:fetch-loans? true})))))))))))

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
                    (replace-in [:territories 0 :territory/number] "123" "123A")
                    (replace-in [:territories 0 :territory/region] "the region" "Some Region")
                    ;; addresses are commonly multiline
                    (replace-in [:territories 0 :territory/addresses] "the addresses" "Some Street\nAnother Street\n"))]
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
           (-> (territory-list-page/view (replace-in model [:territories 0 :territory/number] "123" ""))
               html/visible-text))))

  (testing "territory numbers are sorted naturally"
    (let [territories (shuffle [{:territory/number ""}
                                {:territory/number nil} ; nil should not crash, but be treated same as ""
                                {:territory/number "1"}
                                {:territory/number "2"} ; basic string sort would put this after "10"
                                {:territory/number "10"}
                                {:territory/number "10A"}
                                {:territory/number "10b"} ; sorting should be case-insensitive
                                {:territory/number "10C"}])]
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
    (is (str/includes? (str (territory-list-page/view (replace-in model [:territories 0 :territory/number] "123" "</script>")))
                       "&quot;number&quot;:&quot;&lt;/script&gt;&quot;")))

  (testing "loans lazy loading:"
    ;; Loan data is currently loaded from Google Sheets, which can easily take a couple of seconds.
    ;; Lazy loading is needed to load the territory list page faster and defer rendering the map.
    (let [map-html "<territory-list-map"
          placeholder-icon "{map-location.svg}"]
      (testing "loans disabled -> show map immediately"
        (let [rendered (territory-list-page/view model)]
          (is (str/includes? rendered map-html))
          (is (not (str/includes? (html/visible-text rendered) placeholder-icon)))))

      (testing "loans enabled -> show a placeholder and lazy load the map"
        (let [rendered (territory-list-page/view model-loans-enabled)]
          (is (not (str/includes? rendered map-html)))
          (is (str/includes? (html/visible-text rendered) placeholder-icon))))))

  (testing "limited visibility disclaimer:"
    (testing "anonymous, has opened a share"
      (is (= (html/normalize-whitespace
              "Territories

               {info.svg} Why so few territories?
               Only those territories which have been shared with you are currently shown.
               You will need to login to see the rest.

               Search [] Clear
               Number   Region       Addresses
               123      the region   the addresses")
             (-> (territory-list-page/view anonymous-model)
                 html/visible-text))))

    (testing "logged in without congregation access, has opened a share"
      (is (= (html/normalize-whitespace
              "Territories

               {info.svg} Why so few territories?
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
