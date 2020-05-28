;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.facade-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.facade :as facade]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.config :as config]
            [territory-bro.projections :as projections]
            [territory-bro.test.testutil :as testutil])
  (:import (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def user-id2 (UUID. 0 3))
(def territory-id (UUID. 0 4))
(def territory-id2 (UUID. 0 5))
(def congregation-boundary-id (UUID. 0 6))
(def region-id (UUID. 0 7))
(def card-minimap-viewport-id (UUID. 0 8))

(def congregation-created
  {:event/type :congregation.event/congregation-created
   :congregation/id cong-id
   :congregation/name "Cong1 Name"
   :congregation/schema-name "cong1_schema"})

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

(defn- apply-events [events]
  (testutil/apply-events projections/projection events))

(def events [congregation-created
             view-congregation-granted
             view-congregation-granted2
             territory-defined
             territory-defined2
             congregation-boundary-defined
             region-defined
             card-minimap-viewport-defined])

(deftest test-get-my-congregation
  (let [state (apply-events events)
        expected {:id cong-id
                  :name "Cong1 Name"
                  :permissions {:view-congregation true}
                  :users [{:id user-id}
                          {:id user-id2}]
                  :territories [{:territory/id territory-id
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
                  :congregation-boundaries [{:congregation-boundary/id congregation-boundary-id
                                             :congregation-boundary/location testdata/wkt-multi-polygon}]
                  :regions [{:region/id region-id
                             :region/name "the name"
                             :region/location testdata/wkt-multi-polygon}]
                  :card-minimap-viewports [{:card-minimap-viewport/id card-minimap-viewport-id
                                            :card-minimap-viewport/location testdata/wkt-polygon}]}]

    (testing "no permissions"
      (let [user-id (UUID. 0 0x666)]
        (is (nil? (facade/get-my-congregation state cong-id user-id)))))

    (testing "full view permissions"
      (is (= expected (facade/get-my-congregation state cong-id user-id))))))

(deftest test-get-demo-congregation
  (let [state (apply-events events)
        user-id (UUID. 0 0x666)
        expected {:id "demo" ; changed
                  :name "Demo Congregation" ; changed
                  :permissions {:view-congregation true} ; changed
                  :users [] ; changed
                  :territories [{:territory/id territory-id
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
                  :congregation-boundaries [{:congregation-boundary/id congregation-boundary-id
                                             :congregation-boundary/location testdata/wkt-multi-polygon}]
                  :regions [{:region/id region-id
                             :region/name "the name"
                             :region/location testdata/wkt-multi-polygon}]
                  :card-minimap-viewports [{:card-minimap-viewport/id card-minimap-viewport-id
                                            :card-minimap-viewport/location testdata/wkt-polygon}]}]

    (testing "no demo congregation"
      (is (nil? (facade/get-demo-congregation state user-id))))

    (binding [config/env {:demo-congregation cong-id}]
      (testing "can see the demo congregation"
        (is (= expected (facade/get-demo-congregation state user-id))))

      (testing "cannot see the demo congregation as own congregation"
        (is (nil? (facade/get-my-congregation state cong-id user-id)))))))
