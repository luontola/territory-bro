;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.gis-change-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.gis-change :as gis-change]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.test.testutil :as testutil])
  (:import (java.util UUID)
           (java.time Instant)))

(def cong-id (UUID. 0 1))
(def cong-schema "cong1_schema")

(def user-id (UUID. 0 2))
(def gis-username "gis_user_2")

(def territory-id (UUID. 0 3))
(def region-id (UUID. 0 4))
(def congregation-boundary-id (UUID. 0 5))
(def card-minimap-viewport-id (UUID. 0 6))
(def replacement-id (UUID. 0 0xF))

(def change-id 100)
(def test-time (Instant/ofEpochSecond 10))

(def congregation-created
  {:event/type :congregation.event/congregation-created
   :event/version 1
   :congregation/id cong-id
   :congregation/name ""
   :congregation/schema-name cong-schema})
(def gis-user-created
  {:event/type :congregation.event/gis-user-created
   :event/version 1
   :congregation/id cong-id
   :user/id user-id
   :gis-user/username gis-username
   :gis-user/password ""})

(def gis-change-validator (s/validator gis-db/GisChange))

(defn- change->command [change old-events]
  (let [state (testutil/apply-events gis-change/projection old-events)]
    (-> change
        (gis-change-validator)
        (gis-change/change->command state)
        (testutil/validate-command))))

(deftest change->command-test
  (testing "territory insert"
    (is (= {:command/type :territory.command/create-territory
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :territory/id territory-id
            :territory/number "123"
            :territory/addresses "Street 1 A"
            :territory/subregion "Somewhere"
            :territory/meta {:foo "bar", :gazonk 42}
            :territory/location testdata/wkt-multi-polygon}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "territory"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :INSERT
                :gis-change/old nil
                :gis-change/new {:id territory-id
                                 :number "123"
                                 :addresses "Street 1 A"
                                 :subregion "Somewhere"
                                 :meta {:foo "bar", :gazonk 42}
                                 :location testdata/wkt-multi-polygon}
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "territory update"
    (is (= {:command/type :territory.command/update-territory
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :territory/id territory-id
            :territory/number "123"
            :territory/addresses "Street 1 A"
            :territory/subregion "Somewhere"
            :territory/meta {:foo "bar", :gazonk 42}
            :territory/location testdata/wkt-multi-polygon}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "territory"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :UPDATE
                :gis-change/old {:id territory-id
                                 :number ""
                                 :addresses ""
                                 :subregion ""
                                 :meta {}
                                 :location ""}
                :gis-change/new {:id territory-id
                                 :number "123"
                                 :addresses "Street 1 A"
                                 :subregion "Somewhere"
                                 :meta {:foo "bar", :gazonk 42}
                                 :location testdata/wkt-multi-polygon}
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "territory delete"
    (is (= {:command/type :territory.command/delete-territory
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :territory/id territory-id}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "territory"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :DELETE
                :gis-change/old {:id territory-id
                                 :number "123"
                                 :addresses "Street 1 A"
                                 :subregion "Somewhere"
                                 :meta {:foo "bar", :gazonk 42}
                                 :location testdata/wkt-multi-polygon}
                :gis-change/new nil
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "region insert"
    (is (= {:command/type :region.command/create-region
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :subregion/id region-id
            :subregion/name "Somewhere"
            :subregion/location testdata/wkt-multi-polygon}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "subregion"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :INSERT
                :gis-change/old nil
                :gis-change/new {:id region-id
                                 :name "Somewhere"
                                 :location testdata/wkt-multi-polygon}
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "region update"
    (is (= {:command/type :region.command/update-region
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :subregion/id region-id
            :subregion/name "Somewhere"
            :subregion/location testdata/wkt-multi-polygon}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "subregion"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :UPDATE
                :gis-change/old {:id region-id
                                 :name ""
                                 :location ""}
                :gis-change/new {:id region-id
                                 :name "Somewhere"
                                 :location testdata/wkt-multi-polygon}
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "region delete"
    (is (= {:command/type :region.command/delete-region
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :subregion/id region-id}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "subregion"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :DELETE
                :gis-change/old {:id region-id
                                 :name "Somewhere"
                                 :location testdata/wkt-multi-polygon}
                :gis-change/new nil
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "congregation_boundary insert"
    (is (= {:command/type :congregation-boundary.command/create-congregation-boundary
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :congregation-boundary/id congregation-boundary-id
            :congregation-boundary/location testdata/wkt-multi-polygon}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "congregation_boundary"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :INSERT
                :gis-change/old nil
                :gis-change/new {:id congregation-boundary-id
                                 :location testdata/wkt-multi-polygon}
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "congregation_boundary update"
    (is (= {:command/type :congregation-boundary.command/update-congregation-boundary
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :congregation-boundary/id congregation-boundary-id
            :congregation-boundary/location testdata/wkt-multi-polygon}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "congregation_boundary"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :UPDATE
                :gis-change/old {:id congregation-boundary-id
                                 :location ""}
                :gis-change/new {:id congregation-boundary-id
                                 :location testdata/wkt-multi-polygon}
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "congregation_boundary delete"
    (is (= {:command/type :congregation-boundary.command/delete-congregation-boundary
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :congregation-boundary/id congregation-boundary-id}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "congregation_boundary"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :DELETE
                :gis-change/old {:id congregation-boundary-id
                                 :location testdata/wkt-multi-polygon}
                :gis-change/new nil
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "card_minimap_viewport insert"
    (is (= {:command/type :card-minimap-viewport.command/create-card-minimap-viewport
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :card-minimap-viewport/id card-minimap-viewport-id
            :card-minimap-viewport/location testdata/wkt-polygon}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "card_minimap_viewport"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :INSERT
                :gis-change/old nil
                :gis-change/new {:id card-minimap-viewport-id
                                 :location testdata/wkt-polygon}
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "card_minimap_viewport update"
    (is (= {:command/type :card-minimap-viewport.command/update-card-minimap-viewport
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :card-minimap-viewport/id card-minimap-viewport-id
            :card-minimap-viewport/location testdata/wkt-polygon}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "card_minimap_viewport"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :UPDATE
                :gis-change/old {:id card-minimap-viewport-id
                                 :location ""}
                :gis-change/new {:id card-minimap-viewport-id
                                 :location testdata/wkt-polygon}
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "card_minimap_viewport delete"
    (is (= {:command/type :card-minimap-viewport.command/delete-card-minimap-viewport
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :card-minimap-viewport/id card-minimap-viewport-id}
           (-> {:gis-change/id change-id
                :gis-change/schema cong-schema
                :gis-change/table "card_minimap_viewport"
                :gis-change/user gis-username
                :gis-change/time test-time
                :gis-change/op :DELETE
                :gis-change/old {:id card-minimap-viewport-id
                                 :location testdata/wkt-polygon}
                :gis-change/new nil
                :gis-change/processed? false
                :gis-change/replacement-id nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "replacement ID"
    (testing "insert"
      (is (= {:command/type :region.command/create-region
              :command/system "territory-bro.gis.gis-change"
              :command/user user-id
              :command/time test-time
              :gis-change/id change-id
              :congregation/id cong-id
              :subregion/id replacement-id
              :subregion/name "Somewhere"
              :subregion/location testdata/wkt-multi-polygon}
             (-> {:gis-change/id change-id
                  :gis-change/schema cong-schema
                  :gis-change/table "subregion"
                  :gis-change/user gis-username
                  :gis-change/time test-time
                  :gis-change/op :INSERT
                  :gis-change/old nil
                  :gis-change/new {:id region-id
                                   :name "Somewhere"
                                   :location testdata/wkt-multi-polygon}
                  :gis-change/processed? false
                  :gis-change/replacement-id replacement-id}
                 (change->command [congregation-created gis-user-created])))))

    (testing "update"
      (is (= {:command/type :region.command/update-region
              :command/system "territory-bro.gis.gis-change"
              :command/user user-id
              :command/time test-time
              :gis-change/id change-id
              :congregation/id cong-id
              :subregion/id replacement-id
              :subregion/name "Somewhere"
              :subregion/location testdata/wkt-multi-polygon}
             (-> {:gis-change/id change-id
                  :gis-change/schema cong-schema
                  :gis-change/table "subregion"
                  :gis-change/user gis-username
                  :gis-change/time test-time
                  :gis-change/op :UPDATE
                  :gis-change/old {:id region-id
                                   :name ""
                                   :location ""}
                  :gis-change/new {:id region-id
                                   :name "Somewhere"
                                   :location testdata/wkt-multi-polygon}
                  :gis-change/processed? false
                  :gis-change/replacement-id replacement-id}
                 (change->command [congregation-created gis-user-created])))))

    (testing "delete"
      (is (= {:command/type :region.command/delete-region
              :command/system "territory-bro.gis.gis-change"
              :command/user user-id
              :command/time test-time
              :gis-change/id change-id
              :congregation/id cong-id
              :subregion/id replacement-id}
             (-> {:gis-change/id change-id
                  :gis-change/schema cong-schema
                  :gis-change/table "subregion"
                  :gis-change/user gis-username
                  :gis-change/time test-time
                  :gis-change/op :DELETE
                  :gis-change/old {:id region-id
                                   :name "Somewhere"
                                   :location testdata/wkt-multi-polygon}
                  :gis-change/new nil
                  :gis-change/processed? false
                  :gis-change/replacement-id replacement-id}
                 (change->command [congregation-created gis-user-created])))))

    (testing "update due to ID replacement"
      (is (= {:command/type :region.command/update-region
              :command/system "territory-bro.gis.gis-change"
              :command/user user-id
              :command/time test-time
              :gis-change/id change-id
              :congregation/id cong-id
              :subregion/id replacement-id
              :subregion/name "Somewhere"
              :subregion/location testdata/wkt-multi-polygon}
             (-> {:gis-change/id change-id
                  :gis-change/schema cong-schema
                  :gis-change/table "subregion"
                  :gis-change/user gis-username
                  :gis-change/time test-time
                  :gis-change/op :UPDATE
                  :gis-change/old {:id region-id
                                   :name "Somewhere"
                                   :location testdata/wkt-multi-polygon}
                  :gis-change/new {:id replacement-id
                                   :name "Somewhere"
                                   :location testdata/wkt-multi-polygon}
                  :gis-change/processed? false
                  :gis-change/replacement-id replacement-id}
                 (change->command [congregation-created gis-user-created])))))))
