;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-boundary-test
  (:require [clojure.test :refer :all]
            [territory-bro.congregation-boundary :as congregation-boundary]
            [territory-bro.events :as events]
            [territory-bro.testdata :as testdata]
            [territory-bro.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException)))

(def cong-id (UUID. 0 1))
(def congregation-boundary-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def congregation-boundary-defined
  {:event/type :congregation-boundary.event/congregation-boundary-defined
   :event/version 1
   :congregation/id cong-id
   :congregation-boundary/id congregation-boundary-id
   :congregation-boundary/location testdata/wkt-multi-polygon})
(def congregation-boundary-deleted
  {:event/type :congregation-boundary.event/congregation-boundary-deleted
   :event/version 1
   :congregation/id cong-id
   :congregation-boundary/id congregation-boundary-id})

(defn- apply-events [events]
  (testutil/apply-events congregation-boundary/projection events))

(defn- handle-command [command events injections]
  (->> (congregation-boundary/handle-command (testutil/validate-command command)
                                             (events/validate-events events)
                                             injections)
       (events/validate-events)))

(deftest congregation-boundary-projection-test
  (testing "created"
    (let [events [congregation-boundary-defined]
          expected {::congregation-boundary/congregation-boundaries
                    {cong-id {congregation-boundary-id {:congregation-boundary/id congregation-boundary-id
                                                        :congregation-boundary/location testdata/wkt-multi-polygon}}}}]
      (is (= expected (apply-events events)))

      (testing "> updated"
        (let [events (conj events (assoc congregation-boundary-defined
                                         :congregation-boundary/location "new location"))
              expected (assoc-in expected [::congregation-boundary/congregation-boundaries cong-id congregation-boundary-id
                                           :congregation-boundary/location] "new location")]
          (is (= expected (apply-events events)))))

      (testing "> deleted"
        (let [events (conj events congregation-boundary-deleted)
              expected {}]
          (is (= expected (apply-events events))))))))

(deftest create-congregation-boundary-test
  (let [injections {:check-permit (fn [_permit])}
        create-command {:command/type :congregation-boundary.command/create-congregation-boundary
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :congregation-boundary/id congregation-boundary-id
                        :congregation-boundary/location testdata/wkt-multi-polygon}]

    (testing "created"
      (is (= [congregation-boundary-defined]
             (handle-command create-command [] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command create-command [congregation-boundary-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:create-congregation-boundary cong-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command create-command [] injections)))))))

(deftest update-congregation-boundary-test
  (let [injections {:check-permit (fn [_permit])}
        update-command {:command/type :congregation-boundary.command/update-congregation-boundary
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :congregation-boundary/id congregation-boundary-id
                        :congregation-boundary/location testdata/wkt-multi-polygon}]

    (testing "location changed"
      (is (= [congregation-boundary-defined]
             (handle-command update-command [(assoc congregation-boundary-defined :congregation-boundary/location "old location")] injections))))

    (testing "nothing changed / is idempotent"
      (is (empty? (handle-command update-command [congregation-boundary-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:update-congregation-boundary cong-id congregation-boundary-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command update-command [congregation-boundary-defined] injections)))))))

(deftest delete-congregation-boundary-test
  (let [injections {:check-permit (fn [_permit])}
        delete-command {:command/type :congregation-boundary.command/delete-congregation-boundary
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :congregation-boundary/id congregation-boundary-id}]

    (testing "deleted"
      (is (= [congregation-boundary-deleted]
             (handle-command delete-command [congregation-boundary-defined] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command delete-command [congregation-boundary-defined congregation-boundary-deleted] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:delete-congregation-boundary cong-id congregation-boundary-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command delete-command [congregation-boundary-defined] injections)))))))
  