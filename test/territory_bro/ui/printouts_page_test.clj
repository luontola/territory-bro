;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printouts-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.do-not-calls-test :as do-not-calls-test]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.infra.user :as user]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.map-interaction-help-test :as map-interaction-help-test]
            [territory-bro.ui.printouts-page :as printouts-page])
  (:import (java.time Clock Duration Instant ZoneOffset ZonedDateTime)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def region-id (UUID. 0 2))
(def territory-id (UUID. 0 3))
(def user-id (UUID/randomUUID))
(def default-model
  {:congregation {:congregation/id cong-id
                  :congregation/name "Example Congregation"
                  :congregation/location (str (geometry/parse-wkt testdata/wkt-helsinki))
                  :congregation/timezone testdata/timezone-helsinki}
   :regions [{:region/id region-id
              :region/name "the region"
              :region/location testdata/wkt-south-helsinki}]
   :territories [{:territory/id territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-helsinki-rautatientori}]
   :card-minimap-viewports [testdata/wkt-helsinki]
   :qr-codes-allowed? true
   :form {:template "TerritoryCard"
          :language "en"
          :map-raster "osmhd"
          :regions #{cong-id} ; congregation boundary is shown first in the regions list
          :territories #{territory-id}}
   :mac? false})

(def form-changed-model
  (assoc default-model
         :form {:template "NeighborhoodCard"
                :language "fi"
                :map-raster "mmlTaustakartta"
                :regions #{(UUID. 0 4)
                           (UUID. 0 5)}
                :territories #{(UUID. 0 6)
                               (UUID. 0 7)}}))

(def no-qr-codes-model
  (replace-in default-model [:qr-codes-allowed?] true false))

(def demo-model
  (-> default-model
      (replace-in [:congregation :congregation/id] cong-id "demo")
      (replace-in [:congregation :congregation/name] "Example Congregation" "Demo Congregation")
      (replace-in [:form :regions] #{cong-id} #{"demo"})))

(def test-events
  (flatten [{:event/type :congregation.event/congregation-created
             :congregation/id cong-id
             :congregation/name "Example Congregation"
             :congregation/schema-name "cong1_schema"}
            (congregation/admin-permissions-granted cong-id user-id)
            {:event/type :congregation-boundary.event/congregation-boundary-defined
             :gis-change/id 42
             :congregation/id cong-id
             :congregation-boundary/id (UUID/randomUUID)
             :congregation-boundary/location testdata/wkt-helsinki}
            {:event/type :card-minimap-viewport.event/card-minimap-viewport-defined,
             :gis-change/id 42
             :congregation/id cong-id
             :card-minimap-viewport/id (UUID/randomUUID)
             :card-minimap-viewport/location testdata/wkt-helsinki}
            {:event/type :region.event/region-defined
             :gis-change/id 42
             :congregation/id cong-id
             :region/id region-id
             :region/name "the region"
             :region/location testdata/wkt-south-helsinki}
            {:event/type :territory.event/territory-defined
             :congregation/id cong-id
             :territory/id territory-id
             :territory/number "123"
             :territory/addresses "the addresses"
             :territory/region "the region"
             :territory/meta {:foo "bar"}
             :territory/location testdata/wkt-helsinki-rautatientori}]))

(deftest model!-test
  (let [request {:path-params {:congregation cong-id}}]
    (testutil/with-events test-events
      (testutil/with-user-id user-id

        (testing "default"
          (is (= default-model (printouts-page/model! request))))

        (testing "every form value changed"
          (let [request (assoc request :params {:template "NeighborhoodCard"
                                                :language "fi"
                                                :map-raster "mmlTaustakartta"
                                                :regions [(str (UUID. 0 4))
                                                          (str (UUID. 0 5))]
                                                :territories [(str (UUID. 0 6))
                                                              (str (UUID. 0 7))]})]
            (is (= form-changed-model (printouts-page/model! request)))))

        (testing "no permission to share"
          (binding [dmz/*state* (permissions/revoke dmz/*state* user-id [:share-territory-link cong-id])]
            (is (= no-qr-codes-model (printouts-page/model! request)))))

        (testing "demo congregation"
          (binding [config/env {:demo-congregation cong-id}]
            (let [request {:path-params {:congregation "demo"}}]
              (is (= demo-model
                     (printouts-page/model! request)
                     (testutil/with-anonymous-user
                       (printouts-page/model! request)))))))))))

(deftest parse-uuid-multiselect-test
  (is (= #{} (printouts-page/parse-uuid-multiselect nil)))
  (is (= #{} (printouts-page/parse-uuid-multiselect "")))
  (is (= #{"demo"} (printouts-page/parse-uuid-multiselect "demo")))
  (is (= #{(UUID. 0 1)}
         (printouts-page/parse-uuid-multiselect "00000000-0000-0000-0000-000000000001")))
  (is (= #{(UUID. 0 1)
           (UUID. 0 2)}
         (printouts-page/parse-uuid-multiselect ["00000000-0000-0000-0000-000000000001"
                                                 "00000000-0000-0000-0000-000000000002"]))))

(deftest view-test
  (binding [printouts-page/*clock* (-> (.toInstant (ZonedDateTime/of 2000 12 31 23 59 0 0 testdata/timezone-helsinki))
                                       (Clock/fixed ZoneOffset/UTC))]
    (testing "territory printout"
      (is (= (html/normalize-whitespace
              "Printouts

               Print options
                 Template [Territory card]
                 Language [English]
                 Background map [World - OpenStreetMap (RRZE server, high DPI)]
                 Regions [Example Congregation]
                 Territories [123 - the region]

               Territory Map Card
                 the region
                 123
                 Printed 2000-12-31 with TerritoryBro.com
                 the addresses
                 Please keep this card in the envelope. Do not soil, mark or bend it.
                 Each time the territory is covered, please inform the brother who cares for the territory files."
              map-interaction-help-test/default-visible-text)
             (-> (printouts-page/view default-model)
                 html/visible-text))))

    (testing "region printout"
      (is (= (html/normalize-whitespace
              "Printouts

               Print options
                 Template [Region map]
                 Language [English]
                 Background map [World - OpenStreetMap (RRZE server, high DPI)]
                 Regions [Example Congregation]
                 Territories [123 - the region]

               Example Congregation
                 Printed 2000-12-31 with TerritoryBro.com"
              map-interaction-help-test/default-visible-text)
             (-> (printouts-page/view (assoc-in default-model [:form :template] "RegionPrintout"))
                 html/visible-text))))

    (binding [printouts-page/*clock* (Clock/offset printouts-page/*clock* (Duration/ofMinutes 1))]
      (testing "print date uses the congregation timezone"
        (is (str/includes? (-> (printouts-page/view default-model)
                               html/visible-text)
                           "Printed 2001-01-01"))))

    (testing "hides the 'QR code only' template if creating QR codes is not allowed"
      (let [template-name "QR code only"]
        (is (str/includes? (printouts-page/view default-model) template-name))
        (is (not (str/includes? (printouts-page/view no-qr-codes-model) template-name)))))))


(deftest render-qr-code-svg-test
  (let [svg (printouts-page/render-qr-code-svg "foo")]
    (is (str/includes? svg "viewBox=\"0 0 21 21\""))
    (is (str/includes? svg "M0,0h1v1h-1z M1,0h1v1h-1z"))))

(deftest generate-qr-code!-test
  (let [request {:path-params {:congregation cong-id
                               :territory territory-id}}]
    (testutil/with-events test-events
      (binding [config/env {:now #(Instant/now)}
                do-not-calls/get-do-not-calls do-not-calls-test/fake-get-do-not-calls
                share/generate-share-key (constantly "abcxyz")]
        (testutil/with-user-id user-id
          (with-fixtures [fake-dispatcher-fixture]

            (let [html (printouts-page/generate-qr-code! request)]
              (is (= {:command/type :share.command/create-share
                      :command/user user-id
                      :congregation/id cong-id
                      :territory/id territory-id
                      :share/type :qr-code
                      :share/key "abcxyz"}
                     (dissoc @*last-command :command/time :share/id)))
              (is (str/includes? html "<svg ")))))))))

(deftest ^:slow parallel-qr-code-generation-test
  (with-fixtures [db-fixture]
    (let [request {:path-params {:congregation cong-id
                                 :territory territory-id}}
          handler (dmz/wrap-db-connection printouts-page/generate-qr-code!)
          user-id (db/with-db [conn {}]
                    (user/save-user! conn "test" {}))]
      (testutil/with-events (concat test-events
                                    (congregation/admin-permissions-granted cong-id user-id))
        (testutil/with-user-id user-id
          (testing "avoids database transaction conflicts when many QR codes are generated in parallel"
            (is (every? #(str/starts-with? % "<svg")
                        (->> (repeat 10 #(handler request))
                             (mapv future-call)
                             (mapv deref))))))))))
