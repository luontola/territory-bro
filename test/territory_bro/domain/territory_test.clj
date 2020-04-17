;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.territory-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.territory :as territory]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.events :as events]
            [territory-bro.test.testutil :as testutil :refer [re-equals]])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException)))

(def cong-id (UUID. 0 1))
(def territory-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def gis-change-id 42)
(def territory-defined
  {:event/type :territory.event/territory-defined
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :territory/id territory-id
   :territory/number "123"
   :territory/addresses "the addresses"
   :territory/region "the region"
   :territory/meta {:foo "bar"}
   :territory/location testdata/wkt-multi-polygon})
(def territory-deleted
  {:event/type :territory.event/territory-deleted
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :territory/id territory-id})

(defn- apply-events [events]
  (testutil/apply-events territory/projection events))

(defn- handle-command [command events injections]
  (->> (territory/handle-command (testutil/validate-command command)
                                 (events/validate-events events)
                                 injections)
       (events/validate-events)))

(deftest territory-projection-test
  (testing "created"
    (let [events [territory-defined]
          expected {::territory/territories
                    {cong-id {territory-id {:territory/id territory-id
                                            :territory/number "123"
                                            :territory/addresses "the addresses"
                                            :territory/region "the region"
                                            :territory/meta {:foo "bar"}
                                            :territory/location testdata/wkt-multi-polygon}}}}]
      (is (= expected (apply-events events)))

      (testing "> updated"
        (let [events (conj events (assoc territory-defined
                                         :territory/number "456"
                                         :territory/addresses "new addresses"
                                         :territory/region "new region"
                                         :territory/meta {:new-meta "new stuff"}
                                         :territory/location "new location"))
              expected (update-in expected [::territory/territories cong-id territory-id]
                                  merge {:territory/number "456"
                                         :territory/addresses "new addresses"
                                         :territory/region "new region"
                                         :territory/meta {:new-meta "new stuff"}
                                         :territory/location "new location"})]
          (is (= expected (apply-events events)))))

      (testing "> deleted"
        (let [events (conj events territory-deleted)
              expected {}]
          (is (= expected (apply-events events))))))))


;;;; Queries

(deftest check-territory-exists-test
  (let [state (apply-events [territory-defined])]

    (testing "exists"
      (is (nil? (territory/check-territory-exists state cong-id territory-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-territory #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (territory/check-territory-exists state cong-id (UUID. 0 0x666)))))))


;;;; Commands

(deftest create-territory-test
  (let [injections {:check-permit (fn [_permit])}
        create-command {:command/type :territory.command/create-territory
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :territory/id territory-id
                        :territory/number "123"
                        :territory/addresses "the addresses"
                        :territory/region "the region"
                        :territory/meta {:foo "bar"}
                        :territory/location testdata/wkt-multi-polygon}]

    (testing "created"
      (is (= [territory-defined]
             (handle-command create-command [] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command create-command [territory-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:create-territory cong-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command create-command [] injections)))))))

(deftest update-territory-test
  (let [injections {:check-permit (fn [_permit])}
        update-command {:command/type :territory.command/update-territory
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :territory/id territory-id
                        :territory/number "123"
                        :territory/addresses "the addresses"
                        :territory/region "the region"
                        :territory/meta {:foo "bar"}
                        :territory/location testdata/wkt-multi-polygon}]

    (testing "number changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/number "old number")] injections))))

    (testing "addresses changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/addresses "old addresses")] injections))))

    (testing "region changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/region "old region")] injections))))

    (testing "meta changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/meta {:stuff "old meta"})] injections))))

    (testing "location changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/location "old location")] injections))))

    (testing "nothing changed / is idempotent"
      (is (empty? (handle-command update-command [territory-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:update-territory cong-id territory-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command update-command [territory-defined] injections)))))))

(deftest delete-territory-test
  (let [injections {:check-permit (fn [_permit])}
        delete-command {:command/type :territory.command/delete-territory
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :territory/id territory-id}]

    (testing "deleted"
      (is (= [territory-deleted]
             (handle-command delete-command [territory-defined] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command delete-command [territory-defined territory-deleted] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:delete-territory cong-id territory-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command delete-command [territory-defined] injections)))))))
  