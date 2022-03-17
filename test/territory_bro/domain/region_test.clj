;; Copyright © 2015-2022 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.region-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.events :as events]
            [territory-bro.test.testutil :as testutil :refer [re-equals]])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException)))

(def cong-id (UUID. 0 1))
(def region-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def gis-change-id 42)
(def region-defined
  {:event/type :region.event/region-defined
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :region/id region-id
   :region/name "the name"
   :region/location testdata/wkt-multi-polygon})
(def region-deleted
  {:event/type :region.event/region-deleted
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :region/id region-id})

(defn- apply-events [events]
  (testutil/apply-events region/projection events))

(defn- handle-command [command events injections]
  (->> (region/handle-command (testutil/validate-command command)
                              (events/validate-events events)
                              injections)
       (events/validate-events)))


;;;; Projection

(deftest region-projection-test
  (testing "created"
    (let [events [region-defined]
          expected {::region/regions
                    {cong-id {region-id {:region/id region-id
                                         :region/name "the name"
                                         :region/location testdata/wkt-multi-polygon}}}}]
      (is (= expected (apply-events events)))

      (testing "> updated"
        (let [events (conj events (assoc region-defined
                                         :region/name "new name"
                                         :region/location "new location"))
              expected (-> expected
                           (assoc-in [::region/regions cong-id region-id
                                      :region/name] "new name")
                           (assoc-in [::region/regions cong-id region-id
                                      :region/location] "new location"))]
          (is (= expected (apply-events events)))))

      (testing "> deleted"
        (let [events (conj events region-deleted)
              expected {}]
          (is (= expected (apply-events events))))))))


;;;; Queries

(deftest check-region-exists-test
  (let [state (apply-events [region-defined])]

    (testing "exists"
      (is (nil? (region/check-region-exists state cong-id region-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-region #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (region/check-region-exists state cong-id (UUID. 0 0x666)))))))


;;;; Commands

(deftest define-region-test
  (let [injections {:check-permit (fn [_permit])}
        define-command {:command/type :region.command/define-region
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :region/id region-id
                        :region/name "the name"
                        :region/location testdata/wkt-multi-polygon}]

    (testing "created"
      (is (= [region-defined]
             (handle-command define-command [] injections))))

    (testing "name changed"
      (is (= [region-defined]
             (handle-command define-command [(assoc region-defined :region/name "old name")] injections))))

    (testing "location changed"
      (is (= [region-defined]
             (handle-command define-command [(assoc region-defined :region/location "old location")] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command define-command [region-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:define-region cong-id region-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command define-command [] injections)))))))

(deftest delete-region-test
  (let [injections {:check-permit (fn [_permit])}
        delete-command {:command/type :region.command/delete-region
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :region/id region-id}]

    (testing "deleted"
      (is (= [region-deleted]
             (handle-command delete-command [region-defined] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command delete-command [region-defined region-deleted] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:delete-region cong-id region-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command delete-command [region-defined] injections)))))))
  