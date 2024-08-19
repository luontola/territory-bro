;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.dmz-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.do-not-calls-test :as do-not-calls-test]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.testutil :as testutil])
  (:import (clojure.lang ExceptionInfo)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def user-id2 (UUID. 0 3))
(def territory-id (UUID. 0 4))
(def territory-id2 (UUID. 0 5))
(def congregation-boundary-id (UUID. 0 6))
(def region-id (UUID. 0 7))
(def card-minimap-viewport-id (UUID. 0 8))
(def share-id (UUID. 0 9))
(def share-key "abc123")

(def congregation-created
  {:event/type :congregation.event/congregation-created
   :congregation/id cong-id
   :congregation/name "Cong1 Name"
   :congregation/schema-name "cong1_schema"})

(def settings-updated
  {:event/type :congregation.event/settings-updated
   :congregation/id cong-id
   :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"})

(def view-congregation-granted
  {:event/type :congregation.event/permission-granted
   :congregation/id cong-id
   :user/id user-id
   :permission/id :view-congregation})
(def view-congregation-granted2
  (assoc view-congregation-granted
         :user/id user-id2))

(def territory-defined
  {:event/type :territory.event/territory-defined
   :congregation/id cong-id
   :territory/id territory-id
   :territory/number "123"
   :territory/addresses "the addresses"
   :territory/region "the region"
   :territory/meta {:foo "bar"}
   :territory/location testdata/wkt-helsinki-rautatientori})
(def territory-defined2
  (assoc territory-defined
         :territory/id territory-id2
         :territory/number "456"
         :territory/location testdata/wkt-helsinki-kauppatori))

(def congregation-boundary-defined
  {:event/type :congregation-boundary.event/congregation-boundary-defined
   :congregation/id cong-id
   :congregation-boundary/id congregation-boundary-id
   :congregation-boundary/location testdata/wkt-helsinki})

(def region-defined
  {:event/type :region.event/region-defined
   :congregation/id cong-id
   :region/id region-id
   :region/name "the name"
   :region/location testdata/wkt-south-helsinki})

(def card-minimap-viewport-defined
  {:event/type :card-minimap-viewport.event/card-minimap-viewport-defined
   :congregation/id cong-id
   :card-minimap-viewport/id card-minimap-viewport-id
   :card-minimap-viewport/location testdata/wkt-polygon})

(def share-created
  {:event/type :share.event/share-created
   :share/id share-id
   :share/key share-key
   :share/type :link
   :congregation/id cong-id
   :territory/id territory-id})

(def test-events
  [congregation-created
   settings-updated
   view-congregation-granted
   view-congregation-granted2
   territory-defined
   territory-defined2
   congregation-boundary-defined
   region-defined
   card-minimap-viewport-defined
   share-created])

(defn- apply-share-opened [state]
  (share/grant-opened-shares state [share-id] (auth/current-user-id)))


(deftest get-congregation-test
  (let [expected {:congregation/id cong-id
                  :congregation/name "Cong1 Name"
                  :congregation/timezone testdata/timezone-helsinki
                  :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"
                  :congregation/schema-name "cong1_schema"}
        demo-expected {:congregation/id "demo" ; changed
                       :congregation/name "Demo Congregation" ; changed
                       :congregation/timezone testdata/timezone-helsinki
                       #_:congregation/loans-csv-url ; removed
                       #_:congregation/schema-name}] ; removed

    (testutil/with-events test-events
      (testutil/with-user-id user-id
        (testing "has view permissions"
          (is (= expected (dmz/get-congregation cong-id)))))

      (let [user-id (UUID. 0 0x666)]
        (testutil/with-user-id user-id
          (testing "no permissions"
            (is (thrown-match? ExceptionInfo
                               {:type :ring.util.http-response/response
                                :response {:status 403
                                           :body "No congregation access"
                                           :headers {}}}
                               (dmz/get-congregation cong-id))))

          (testing "demo congregation"
            (binding [config/env {:demo-congregation cong-id}]
              (is (= demo-expected (dmz/get-congregation "demo")))))

          (testing "opened a share"
            (binding [dmz/*state* (apply-share-opened dmz/*state*)]
              (is (= expected (dmz/get-congregation cong-id))))))))))

(deftest get-territory-test
  (let [expected {:congregation/id cong-id
                  :territory/id territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/do-not-calls "the do-not-calls"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-helsinki-rautatientori}
        demo-expected {:congregation/id "demo" ; changed
                       :territory/id territory-id
                       :territory/number "123"
                       :territory/addresses "the addresses"
                       :territory/region "the region"
                       #_:territory/do-not-calls ; removed
                       :territory/meta {:foo "bar"}
                       :territory/location testdata/wkt-helsinki-rautatientori}]

    (binding [do-not-calls/get-do-not-calls do-not-calls-test/fake-get-do-not-calls]
      (testutil/with-events test-events
        (testutil/with-user-id user-id
          (testing "has view permissions"
            (is (= expected (dmz/get-territory cong-id territory-id)))))

        (let [user-id (UUID. 0 0x666)]
          (testutil/with-user-id user-id
            (testing "no permissions"
              (is (thrown-match? ExceptionInfo
                                 {:type :ring.util.http-response/response
                                  :response {:status 403
                                             :body "No territory access"
                                             :headers {}}}
                                 (dmz/get-territory cong-id territory-id))))

            (testing "demo congregation"
              (binding [config/env {:demo-congregation cong-id}]
                (is (= demo-expected (dmz/get-territory "demo" territory-id)))))

            (testing "opened a share"
              (binding [dmz/*state* (apply-share-opened dmz/*state*)]
                (is (= expected (dmz/get-territory cong-id territory-id)))))))))))

(deftest list-territories-test
  (let [expected [{:territory/id territory-id
                   :territory/number "123"
                   :territory/addresses "the addresses"
                   :territory/region "the region"
                   :territory/meta {:foo "bar"}
                   :territory/location testdata/wkt-helsinki-rautatientori}
                  {:territory/id territory-id2
                   :territory/number "456"
                   :territory/addresses "the addresses"
                   :territory/region "the region"
                   :territory/meta {:foo "bar"}
                   :territory/location testdata/wkt-helsinki-kauppatori}]]

    (testutil/with-events test-events
      (testutil/with-user-id user-id
        (testing "has view permissions"
          (is (= expected (dmz/list-territories cong-id nil)))))

      (let [user-id (UUID. 0 0x666)]
        (testutil/with-user-id user-id
          (testing "no permissions"
            (is (nil? (dmz/list-territories cong-id nil))))

          (testing "demo congregation"
            (binding [config/env {:demo-congregation cong-id}]
              (is (= expected (dmz/list-territories "demo" nil)))))

          (testing "opened a share"
            (binding [dmz/*state* (apply-share-opened dmz/*state*)]
              (is (= (take 1 expected) (dmz/list-territories cong-id nil))))))))))

(deftest get-congregation-boundary-test
  (let [expected testdata/wkt-helsinki]

    (testutil/with-events test-events
      (testutil/with-user-id user-id
        (testing "has view permissions"
          (is (= expected (dmz/get-congregation-boundary cong-id)))))

      (let [user-id (UUID. 0 0x666)]
        (testutil/with-user-id user-id
          (testing "no permissions"
            (is (nil? (dmz/get-congregation-boundary cong-id))))

          (testing "demo congregation"
            (binding [config/env {:demo-congregation cong-id}]
              (is (= expected (dmz/get-congregation-boundary "demo")))))

          (testing "opened a share"
            (binding [dmz/*state* (apply-share-opened dmz/*state*)]
              (is (nil? (dmz/get-congregation-boundary cong-id))))))))))

(deftest list-regions-test
  (let [expected [{:region/id region-id
                   :region/name "the name"
                   :region/location testdata/wkt-south-helsinki}]]

    (testutil/with-events test-events
      (testutil/with-user-id user-id
        (testing "has view permissions"
          (is (= expected (dmz/list-regions cong-id)))))

      (let [user-id (UUID. 0 0x666)]
        (testutil/with-user-id user-id
          (testing "no permissions"
            (is (nil? (dmz/list-regions cong-id))))

          (testing "demo congregation"
            (binding [config/env {:demo-congregation cong-id}]
              (is (= expected (dmz/list-regions "demo")))))

          (testing "opened a share"
            (binding [dmz/*state* (apply-share-opened dmz/*state*)]
              (is (nil? (dmz/list-regions cong-id))))))))))

(deftest list-card-minimap-viewports-test
  (let [expected [{:card-minimap-viewport/id card-minimap-viewport-id
                   :card-minimap-viewport/location testdata/wkt-polygon}]]

    (testutil/with-events test-events
      (testutil/with-user-id user-id
        (testing "has view permissions"
          (is (= expected (dmz/list-card-minimap-viewports cong-id)))))

      (let [user-id (UUID. 0 0x666)]
        (testutil/with-user-id user-id
          (testing "no permissions"
            (is (nil? (dmz/list-card-minimap-viewports cong-id))))

          (testing "demo congregation"
            (binding [config/env {:demo-congregation cong-id}]
              (is (= expected (dmz/list-card-minimap-viewports "demo")))))

          (testing "opened a share"
            (binding [dmz/*state* (apply-share-opened dmz/*state*)]
              (is (nil? (dmz/list-card-minimap-viewports cong-id))))))))))
