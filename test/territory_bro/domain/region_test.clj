;; Copyright © 2015-2020 Esko Luontola
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
(def subregion-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def gis-change-id 42)
(def subregion-defined
  {:event/type :subregion.event/subregion-defined
   :event/version 1
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :subregion/id subregion-id
   :subregion/name "the name"
   :subregion/location testdata/wkt-multi-polygon})
(def subregion-deleted
  {:event/type :subregion.event/subregion-deleted
   :event/version 1
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :subregion/id subregion-id})

(defn- apply-events [events]
  (testutil/apply-events region/projection events))

(defn- handle-command [command events injections]
  (->> (region/handle-command (testutil/validate-command command)
                              (events/validate-events events)
                              injections)
       (events/validate-events)))


;;;; Projection

(deftest subregion-projection-test
  (testing "created"
    (let [events [subregion-defined]
          expected {::region/subregions
                    {cong-id {subregion-id {:subregion/id subregion-id
                                            :subregion/name "the name"
                                            :subregion/location testdata/wkt-multi-polygon}}}}]
      (is (= expected (apply-events events)))

      (testing "> updated"
        (let [events (conj events (assoc subregion-defined
                                         :subregion/name "new name"
                                         :subregion/location "new location"))
              expected (-> expected
                           (assoc-in [::region/subregions cong-id subregion-id
                                      :subregion/name] "new name")
                           (assoc-in [::region/subregions cong-id subregion-id
                                      :subregion/location] "new location"))]
          (is (= expected (apply-events events)))))

      (testing "> deleted"
        (let [events (conj events subregion-deleted)
              expected {}]
          (is (= expected (apply-events events))))))))


;;;; Queries

(deftest check-subregion-exists-test
  (let [state (apply-events [subregion-defined])]

    (testing "exists"
      (is (nil? (region/check-subregion-exists state cong-id subregion-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-subregion #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (region/check-subregion-exists state cong-id (UUID. 0 0x666)))))))


;;;; Commands

(deftest create-subregion-test
  (let [injections {:check-permit (fn [_permit])}
        create-command {:command/type :subregion.command/create-subregion
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :subregion/id subregion-id
                        :subregion/name "the name"
                        :subregion/location testdata/wkt-multi-polygon}]

    (testing "created"
      (is (= [subregion-defined]
             (handle-command create-command [] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command create-command [subregion-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:create-subregion cong-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command create-command [] injections)))))))

(deftest update-subregion-test
  (let [injections {:check-permit (fn [_permit])}
        update-command {:command/type :subregion.command/update-subregion
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :subregion/id subregion-id
                        :subregion/name "the name"
                        :subregion/location testdata/wkt-multi-polygon}]

    (testing "name changed"
      (is (= [subregion-defined]
             (handle-command update-command [(assoc subregion-defined :subregion/name "old name")] injections))))

    (testing "location changed"
      (is (= [subregion-defined]
             (handle-command update-command [(assoc subregion-defined :subregion/location "old location")] injections))))

    (testing "nothing changed / is idempotent"
      (is (empty? (handle-command update-command [subregion-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:update-subregion cong-id subregion-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command update-command [subregion-defined] injections)))))))

(deftest delete-subregion-test
  (let [injections {:check-permit (fn [_permit])}
        delete-command {:command/type :subregion.command/delete-subregion
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :subregion/id subregion-id}]

    (testing "deleted"
      (is (= [subregion-deleted]
             (handle-command delete-command [subregion-defined] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command delete-command [subregion-defined subregion-deleted] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:delete-subregion cong-id subregion-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command delete-command [subregion-defined] injections)))))))
  