;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.dmz-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.testutil :as testutil])
  (:import (java.time Instant)
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
   :territory/location testdata/wkt-multi-polygon})
(def territory-defined2
  (assoc territory-defined
         :territory/id territory-id2
         :territory/number "456"))

(def congregation-boundary-defined
  {:event/type :congregation-boundary.event/congregation-boundary-defined
   :congregation/id cong-id
   :congregation-boundary/id congregation-boundary-id
   :congregation-boundary/location testdata/wkt-multi-polygon})

(def region-defined
  {:event/type :region.event/region-defined
   :congregation/id cong-id
   :region/id region-id
   :region/name "the name"
   :region/location testdata/wkt-multi-polygon})

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

(def fake-conn ::fake-conn)

(defn fake-get-do-not-calls [conn -cong-id -territory-id]
  (is (some? conn)
      "get-do-not-calls conn")
  (is (= cong-id -cong-id)
      "get-do-not-calls cong-id")
  (is (= territory-id -territory-id)
      "get-do-not-calls territory-id")
  {:congregation/id -cong-id
   :territory/id -territory-id
   :territory/do-not-calls "the do-not-calls"
   :do-not-calls/last-modified (Instant/now)})


(deftest test-get-congregation
  (let [expected {:congregation/id cong-id
                  :congregation/name "Cong1 Name"
                  :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"
                  :congregation/permissions {:view-congregation true}
                  :congregation/users [{:user/id user-id}
                                       {:user/id user-id2}]
                  :congregation/territories [{:territory/id territory-id
                                              :territory/number "123"
                                              :territory/addresses "the addresses"
                                              :territory/region "the region"
                                              :territory/meta {:foo "bar"}
                                              :territory/location testdata/wkt-multi-polygon}
                                             {:territory/id territory-id2
                                              :territory/number "456"
                                              :territory/addresses "the addresses"
                                              :territory/region "the region"
                                              :territory/meta {:foo "bar"}
                                              :territory/location testdata/wkt-multi-polygon}]
                  :congregation/congregation-boundaries [{:congregation-boundary/id congregation-boundary-id
                                                          :congregation-boundary/location testdata/wkt-multi-polygon}]
                  :congregation/regions [{:region/id region-id
                                          :region/name "the name"
                                          :region/location testdata/wkt-multi-polygon}]
                  :congregation/card-minimap-viewports [{:card-minimap-viewport/id card-minimap-viewport-id
                                                         :card-minimap-viewport/location testdata/wkt-polygon}]}]
    (testutil/with-events test-events
      (auth/with-user-id user-id
        (testing "has view permissions"
          (is (= expected (dmz/get-own-congregation cong-id)))))

      (let [user-id (UUID. 0 0x666)]
        (auth/with-user-id user-id
          (testing "no permissions"
            (is (nil? (dmz/get-own-congregation cong-id))))

          (testing "opened a share"
            (let [expected (assoc expected
                                  :congregation/permissions {}
                                  :congregation/users []
                                  :congregation/territories [{:territory/id territory-id
                                                              :territory/number "123"
                                                              :territory/addresses "the addresses"
                                                              :territory/region "the region"
                                                              :territory/meta {:foo "bar"}
                                                              :territory/location testdata/wkt-multi-polygon}]
                                  :congregation/congregation-boundaries []
                                  :congregation/regions []
                                  :congregation/card-minimap-viewports [])]
              (binding [dmz/*state* (share/grant-opened-shares dmz/*state* [share-id] user-id)]
                (is (= expected (dmz/get-own-congregation cong-id)))))))))))

(deftest test-get-demo-congregation
  (let [user-id (UUID. 0 0x666)
        expected {:congregation/id "demo" ; changed
                  :congregation/name "Demo Congregation" ; changed
                  :congregation/loans-csv-url nil ; changed
                  :congregation/permissions {:view-congregation true
                                             :share-territory-link true} ; changed
                  :congregation/users [] ; changed
                  :congregation/territories [{:territory/id territory-id
                                              :territory/number "123"
                                              :territory/addresses "the addresses"
                                              :territory/region "the region"
                                              :territory/meta {:foo "bar"}
                                              :territory/location testdata/wkt-multi-polygon}
                                             {:territory/id territory-id2
                                              :territory/number "456"
                                              :territory/addresses "the addresses"
                                              :territory/region "the region"
                                              :territory/meta {:foo "bar"}
                                              :territory/location testdata/wkt-multi-polygon}]
                  :congregation/congregation-boundaries [{:congregation-boundary/id congregation-boundary-id
                                                          :congregation-boundary/location testdata/wkt-multi-polygon}]
                  :congregation/regions [{:region/id region-id
                                          :region/name "the name"
                                          :region/location testdata/wkt-multi-polygon}]
                  :congregation/card-minimap-viewports [{:card-minimap-viewport/id card-minimap-viewport-id
                                                         :card-minimap-viewport/location testdata/wkt-polygon}]}]
    (testutil/with-events test-events
      (auth/with-user-id user-id
        (testing "no demo congregation"
          (is (nil? (dmz/get-demo-congregation nil))))

        (testing "can see the demo congregation"
          (is (= expected (dmz/get-demo-congregation cong-id))))

        (testing "cannot see the demo congregation as own congregation"
          (is (nil? (dmz/get-own-congregation cong-id))))))))

(deftest test-get-territory
  (let [expected {:congregation/id cong-id
                  :territory/id territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/do-not-calls "the do-not-calls"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-multi-polygon}]
    (binding [do-not-calls/get-do-not-calls fake-get-do-not-calls]
      (testutil/with-events test-events
        (auth/with-user-id user-id
          (testing "has view permissions"
            (is (= expected (dmz/get-own-territory cong-id territory-id)))))

        (let [user-id (UUID. 0 0x666)]
          (auth/with-user-id user-id
            (testing "no permissions"
              (is (nil? (dmz/get-own-territory cong-id territory-id))))

            (testing "opened a share"
              (binding [dmz/*state* (share/grant-opened-shares dmz/*state* [share-id] user-id)]
                (is (= expected (dmz/get-own-territory cong-id territory-id)))))))))))

(deftest test-get-demo-territory
  (let [expected {:congregation/id "demo" ; changed
                  :territory/id territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  ;; no do-not-calls
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-multi-polygon}]
    (testutil/with-events test-events
      (binding [do-not-calls/get-do-not-calls (fn [& _]
                                                (assert false "should not have been called"))]
        (testing "no demo congregation"
          (is (nil? (dmz/get-demo-territory nil territory-id))))

        (testing "can see the demo congregation"
          (is (= expected (dmz/get-demo-territory cong-id territory-id))))

        (testing "wrong territory ID"
          (is (nil? (dmz/get-demo-territory cong-id (UUID. 0 0x666))))))

      (let [user-id (UUID. 0 0x666)]
        (auth/with-user-id user-id
          (binding [do-not-calls/get-do-not-calls fake-get-do-not-calls]
            (testing "cannot see the demo congregation as own congregation"
              (is (nil? (dmz/get-own-territory cong-id territory-id))))))))))
