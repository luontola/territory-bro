;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.congregation-boundary-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.events :as events]
            [territory-bro.test.testutil :as testutil :refer [re-equals thrown-with-msg? thrown?]])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException)))

(def cong-id (UUID. 0 1))
(def congregation-boundary-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def gis-change-id 42)
(def congregation-boundary-defined
  {:event/type :congregation-boundary.event/congregation-boundary-defined
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :congregation-boundary/id congregation-boundary-id
   :congregation-boundary/location testdata/wkt-multi-polygon})
(def congregation-boundary-deleted
  {:event/type :congregation-boundary.event/congregation-boundary-deleted
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :congregation-boundary/id congregation-boundary-id})

(defn- apply-events [events]
  (testutil/apply-events congregation-boundary/projection events))

(defn- handle-command [command events injections]
  (->> (congregation-boundary/handle-command (testutil/validate-command command)
                                             (events/validate-events events)
                                             injections)
       (events/validate-events)))


;;;; Projection

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


;;;; Queries

(deftest check-congregation-boundary-exists-test
  (let [state (apply-events [congregation-boundary-defined])]

    (testing "exists"
      (is (nil? (congregation-boundary/check-congregation-boundary-exists state cong-id congregation-boundary-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-congregation-boundary #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (congregation-boundary/check-congregation-boundary-exists state cong-id (UUID. 0 0x666)))))))


;;;; Commands

(deftest define-congregation-boundary-test
  (let [injections {:check-permit (fn [_permit])}
        define-command {:command/type :congregation-boundary.command/define-congregation-boundary
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :congregation-boundary/id congregation-boundary-id
                        :congregation-boundary/location testdata/wkt-multi-polygon}]

    (testing "created"
      (is (= [congregation-boundary-defined]
             (handle-command define-command [] injections))))

    (testing "location changed"
      (is (= [congregation-boundary-defined]
             (handle-command define-command [(assoc congregation-boundary-defined :congregation-boundary/location "old location")] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command define-command [congregation-boundary-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:define-congregation-boundary cong-id congregation-boundary-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command define-command [] injections)))))))

(deftest delete-congregation-boundary-test
  (let [injections {:check-permit (fn [_permit])}
        delete-command {:command/type :congregation-boundary.command/delete-congregation-boundary
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
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
  