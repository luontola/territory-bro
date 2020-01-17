;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-sync-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-sync :as gis-sync]
            [territory-bro.testdata :as testdata]
            [territory-bro.testutil :as testutil])
  (:import (java.util UUID)
           (java.time Instant)))

(def cong-id (UUID. 0 1))
(def cong-schema "cong1_schema")

(def user-id (UUID. 0 2))
(def gis-username "gis_user_2")

(def territory-id (UUID. 0 3))
(def subregion-id (UUID. 0 4))
(def congregation-boundary-id (UUID. 0 5))
(def card-minimap-viewport-id (UUID. 0 6))

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
  (let [state (testutil/apply-events gis-sync/projection old-events)]
    (-> change
        (gis-change-validator)
        (gis-sync/change->command state))))

(deftest change->command-test

  (testing "territory insert"
    (is (= {:command/type :territory.command/create-territory
            :command/user user-id
            :command/time test-time
            :congregation/id cong-id
            :territory/id territory-id
            :territory/number "123"
            :territory/addresses "Street 1 A"
            :territory/subregion "Somewhere"
            :territory/meta {:foo "bar", :gazonk 42}
            :territory/location testdata/wkt-multi-polygon}
           (-> {:id change-id
                :schema cong-schema
                :table "territory"
                :user gis-username
                :time test-time
                :op :INSERT
                :old nil
                :new {:id territory-id
                      :number "123"
                      :addresses "Street 1 A"
                      :subregion "Somewhere"
                      :meta {:foo "bar", :gazonk 42}
                      :location testdata/wkt-multi-polygon}}
               (change->command [congregation-created gis-user-created])))))

  (testing "territory update"
    (is (= {:command/type :territory.command/update-territory
            :command/user user-id
            :command/time test-time
            :congregation/id cong-id
            :territory/id territory-id
            :territory/number "123"
            :territory/addresses "Street 1 A"
            :territory/subregion "Somewhere"
            :territory/meta {:foo "bar", :gazonk 42}
            :territory/location testdata/wkt-multi-polygon}
           (-> {:id change-id
                :schema cong-schema
                :table "territory"
                :user gis-username
                :time test-time
                :op :UPDATE
                :old {:id territory-id
                      :number ""
                      :addresses ""
                      :subregion ""
                      :meta {}
                      :location ""}
                :new {:id territory-id
                      :number "123"
                      :addresses "Street 1 A"
                      :subregion "Somewhere"
                      :meta {:foo "bar", :gazonk 42}
                      :location testdata/wkt-multi-polygon}}
               (change->command [congregation-created gis-user-created])))))

  (testing "territory delete"
    (is (= {:command/type :territory.command/delete-territory
            :command/user user-id
            :command/time test-time
            :congregation/id cong-id
            :territory/id territory-id}
           (-> {:id change-id
                :schema cong-schema
                :table "territory"
                :user gis-username
                :time test-time
                :op :DELETE
                :old {:id territory-id
                      :number "123"
                      :addresses "Street 1 A"
                      :subregion "Somewhere"
                      :meta {:foo "bar", :gazonk 42}
                      :location testdata/wkt-multi-polygon}
                :new nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "subregion insert"
    (is (= {:command/type :subregion.command/create-subregion
            :command/user user-id
            :command/time test-time
            :congregation/id cong-id
            :subregion/id subregion-id
            :subregion/name "Somewhere"
            :subregion/location testdata/wkt-multi-polygon}
           (-> {:id change-id
                :schema cong-schema
                :table "subregion"
                :user gis-username
                :time test-time
                :op :INSERT
                :old nil
                :new {:id subregion-id
                      :name "Somewhere"
                      :location testdata/wkt-multi-polygon}}
               (change->command [congregation-created gis-user-created])))))

  (testing "subregion update"
    (is (= {:command/type :subregion.command/update-subregion
            :command/user user-id
            :command/time test-time
            :congregation/id cong-id
            :subregion/id subregion-id
            :subregion/name "Somewhere"
            :subregion/location testdata/wkt-multi-polygon}
           (-> {:id change-id
                :schema cong-schema
                :table "subregion"
                :user gis-username
                :time test-time
                :op :UPDATE
                :old {:id subregion-id
                      :name ""
                      :location ""}
                :new {:id subregion-id
                      :name "Somewhere"
                      :location testdata/wkt-multi-polygon}}
               (change->command [congregation-created gis-user-created])))))

  (testing "subregion delete"
    (is (= {:command/type :subregion.command/delete-subregion
            :command/user user-id
            :command/time test-time
            :congregation/id cong-id
            :subregion/id subregion-id}
           (-> {:id change-id
                :schema cong-schema
                :table "subregion"
                :user gis-username
                :time test-time
                :op :DELETE
                :old {:id subregion-id
                      :name "Somewhere"
                      :location testdata/wkt-multi-polygon}
                :new nil}
               (change->command [congregation-created gis-user-created])))))

  (testing "congregation_boundary insert") ; TODO
  (testing "congregation_boundary update") ; TODO
  (testing "congregation_boundary delete") ; TODO

  (testing "card_minimap_viewport insert") ; TODO
  (testing "card_minimap_viewport update") ; TODO
  (testing "card_minimap_viewport delete")) ; TODO
