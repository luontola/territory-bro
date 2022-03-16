;; Copyright © 2015-2022 Esko Luontola
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

(def change-id 100)
(def test-time (Instant/ofEpochSecond 10))

(def congregation-created
  {:event/type :congregation.event/congregation-created
   :congregation/id cong-id
   :congregation/name ""
   :congregation/schema-name cong-schema})
(def gis-user-created
  {:event/type :congregation.event/gis-user-created
   :congregation/id cong-id
   :user/id user-id
   :gis-user/username gis-username
   :gis-user/password ""})

(deftest normalize-change-test
  (testing "normal event"
    (let [change {:gis-change/id change-id
                  :gis-change/op :UPDATE
                  :gis-change/old {:id territory-id
                                   :number "100"}
                  :gis-change/new {:id territory-id
                                   :number "200"}}]
      (is (= [change]
             (gis-change/normalize-change change)))))

  (testing "ID change"
    (let [territory-id2 (UUID/randomUUID)
          change {:gis-change/id change-id
                  :gis-change/op :UPDATE
                  :gis-change/old {:id territory-id
                                   :number "100"}
                  :gis-change/new {:id territory-id2
                                   :number "200"}}
          delete {:gis-change/id change-id
                  :gis-change/op :DELETE
                  :gis-change/old {:id territory-id
                                   :number "100"}
                  :gis-change/new nil}
          insert {:gis-change/id change-id
                  :gis-change/op :INSERT
                  :gis-change/old nil
                  :gis-change/new {:id territory-id2
                                   :number "200"}}]
      (is (= [delete insert]
             (gis-change/normalize-change change))))))

(def gis-change-validator (s/validator gis-db/GisChange))

(defn- change->command [change old-events]
  (let [state (testutil/apply-events gis-change/projection old-events)]
    (-> change
        (gis-change-validator)
        (gis-change/change->command state)
        (testutil/validate-command))))

(deftest change->command-test
  (testing "territory insert"
    (is (= {:command/type :territory.command/define-territory
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :territory/id territory-id
            :territory/number "123"
            :territory/addresses "Street 1 A"
            :territory/region "Somewhere"
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
                :gis-change/processed? false}
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
            :territory/region "Somewhere"
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
                :gis-change/processed? false}
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
                :gis-change/processed? false}
               (change->command [congregation-created gis-user-created])))))

  (testing "region insert"
    (is (= {:command/type :region.command/define-region
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :region/id region-id
            :region/name "Somewhere"
            :region/location testdata/wkt-multi-polygon}
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
                :gis-change/processed? false}
               (change->command [congregation-created gis-user-created])))))

  (testing "region update"
    (is (= {:command/type :region.command/update-region
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :region/id region-id
            :region/name "Somewhere"
            :region/location testdata/wkt-multi-polygon}
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
                :gis-change/processed? false}
               (change->command [congregation-created gis-user-created])))))

  (testing "region delete"
    (is (= {:command/type :region.command/delete-region
            :command/system "territory-bro.gis.gis-change"
            :command/user user-id
            :command/time test-time
            :gis-change/id change-id
            :congregation/id cong-id
            :region/id region-id}
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
                :gis-change/processed? false}
               (change->command [congregation-created gis-user-created])))))

  (testing "congregation_boundary insert"
    (is (= {:command/type :congregation-boundary.command/define-congregation-boundary
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
                :gis-change/processed? false}
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
                :gis-change/processed? false}
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
                :gis-change/processed? false}
               (change->command [congregation-created gis-user-created])))))

  (testing "card_minimap_viewport insert"
    (is (= {:command/type :card-minimap-viewport.command/define-card-minimap-viewport
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
                :gis-change/processed? false}
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
                :gis-change/processed? false}
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
                :gis-change/processed? false}
               (change->command [congregation-created gis-user-created]))))))
