;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.territory-test
  (:require [clojure.test :refer :all]
            [medley.core :refer [dissoc-in]]
            [territory-bro.domain.territory :as territory]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.events :as events]
            [territory-bro.test.testutil :as testutil :refer [re-equals thrown-with-msg? thrown?]])
  (:import (java.time Instant LocalDate)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException)))

(def cong-id (UUID. 0 1))
(def territory-id (UUID. 0 2))
(def assignment-id (UUID. 0 3))
(def publisher-id (UUID. 0 4))
(def user-id (UUID. 0 5))
(def gis-change-id 42)
(def start-date (LocalDate/of 2000 1 1))
(def end-date (LocalDate/of 2000 1 31))

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

(def territory-assigned
  {:event/type :territory.event/territory-assigned
   :congregation/id cong-id
   :territory/id territory-id
   :assignment/id assignment-id
   :assignment/start-date start-date
   :publisher/id publisher-id})

(def territory-covered
  {:event/type :territory.event/territory-covered
   :congregation/id cong-id
   :territory/id territory-id
   :assignment/id assignment-id
   :assignment/covered-date end-date})

(def territory-returned
  {:event/type :territory.event/territory-returned
   :congregation/id cong-id
   :territory/id territory-id
   :assignment/id assignment-id
   :assignment/end-date end-date})

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

      (testing "> assigned"
        (let [events (conj events territory-assigned)
              expected-assignment {:assignment/id assignment-id
                                   :assignment/start-date start-date
                                   :publisher/id publisher-id}
              expected (-> expected
                           (assoc-in [::territory/territories cong-id territory-id :territory/current-assignment] expected-assignment)
                           (assoc-in [::territory/territories cong-id territory-id :territory/assignments assignment-id] expected-assignment))]
          (is (= expected (apply-events events)))

          (testing "> covered"
            (let [events (conj events territory-covered)
                  expected-assignment (assoc expected-assignment :assignment/covered-dates #{end-date})
                  expected (-> expected
                               (assoc-in [::territory/territories cong-id territory-id :territory/last-covered] end-date)
                               (assoc-in [::territory/territories cong-id territory-id :territory/current-assignment] expected-assignment)
                               (assoc-in [::territory/territories cong-id territory-id :territory/assignments assignment-id] expected-assignment))]
              (is (= expected (apply-events events)))

              (testing "> covered many times"
                (let [end-date2 (LocalDate/of 2000 2 3)
                      events (conj events (assoc territory-covered :assignment/covered-date end-date2))
                      expected-assignment (assoc expected-assignment :assignment/covered-dates #{end-date end-date2})
                      expected (-> expected
                                   (assoc-in [::territory/territories cong-id territory-id :territory/last-covered] end-date2)
                                   (assoc-in [::territory/territories cong-id territory-id :territory/current-assignment] expected-assignment)
                                   (assoc-in [::territory/territories cong-id territory-id :territory/assignments assignment-id] expected-assignment))]
                  (is (= expected (apply-events events)))))

              (testing "> returned"
                (let [events (conj events territory-returned)
                      expected-assignment (assoc expected-assignment :assignment/end-date end-date)
                      expected (-> expected
                                   (dissoc-in [::territory/territories cong-id territory-id :territory/current-assignment])
                                   (assoc-in [::territory/territories cong-id territory-id :territory/assignments assignment-id] expected-assignment))]
                  (is (= expected (apply-events events)))))))))

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

(deftest define-territory-test
  (let [injections {:check-permit (fn [_permit])}
        define-command {:command/type :territory.command/define-territory
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
             (handle-command define-command [] injections))))

    (testing "number changed"
      (is (= [territory-defined]
             (handle-command define-command [(assoc territory-defined :territory/number "old number")] injections))))

    (testing "addresses changed"
      (is (= [territory-defined]
             (handle-command define-command [(assoc territory-defined :territory/addresses "old addresses")] injections))))

    (testing "region changed"
      (is (= [territory-defined]
             (handle-command define-command [(assoc territory-defined :territory/region "old region")] injections))))

    (testing "meta changed"
      (is (= [territory-defined]
             (handle-command define-command [(assoc territory-defined :territory/meta {:stuff "old meta"})] injections))))

    (testing "location changed"
      (is (= [territory-defined]
             (handle-command define-command [(assoc territory-defined :territory/location "old location")] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command define-command [territory-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:define-territory cong-id territory-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command define-command [] injections)))))))

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

(deftest assign-territory-test
  (let [injections {:check-permit (fn [_permit])}
        assign-command {:command/type :territory.command/assign-territory
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :territory/id territory-id
                        :assignment/id assignment-id
                        :date start-date
                        :publisher/id publisher-id}]

    (testing "assigned"
      (is (= [territory-assigned]
             (handle-command assign-command [territory-defined] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command assign-command [territory-defined territory-assigned] injections))))

    (testing "cannot create a new assignment if one is already active"
      (let [assign-command (assoc assign-command :assignment/id (UUID/randomUUID))]
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:already-assigned #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000002\"]]")
             (handle-command assign-command [territory-defined territory-assigned] injections)))))

    (testing "cannot reuse an old assignment ID"
      ;; The command handler should not check just the latest assignment,
      ;; but all assignment IDs that are tied to this territory.
      (let [assignment-id2 (UUID/randomUUID)]
        (is (empty? (handle-command assign-command [territory-defined
                                                    territory-assigned
                                                    territory-returned
                                                    (assoc territory-assigned :assignment/id assignment-id2)
                                                    (assoc territory-returned :assignment/id assignment-id2)]
                                    injections)))))

    (testing "can assign after territory has been returned"
      (let [assignment-id2 (UUID/randomUUID)
            assign-command (assoc assign-command :assignment/id assignment-id2)]
        (is (= [(assoc territory-assigned :assignment/id assignment-id2)]
               (handle-command assign-command [territory-defined territory-assigned territory-returned] injections)))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:assign-territory cong-id territory-id publisher-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command assign-command [territory-defined] injections)))))))

(deftest return-territory-test
  (let [injections {:check-permit (fn [_permit])}
        return-command {:command/type :territory.command/return-territory
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :territory/id territory-id
                        :assignment/id assignment-id
                        :date end-date
                        :returning? true
                        :covered? true}
        only-returning-command (assoc return-command :covered? false)
        only-mark-covered-command (assoc return-command :returning? false)]

    (testing "returned & covered"
      (is (= [territory-covered territory-returned]
             (handle-command return-command [territory-defined territory-assigned] injections))))

    (testing "only returning"
      (is (= [territory-returned]
             (handle-command only-returning-command [territory-defined territory-assigned] injections))))

    (testing "only marking as covered"
      (is (= [territory-covered]
             (handle-command only-mark-covered-command [territory-defined territory-assigned] injections))))

    (testing "neither returning nor marking as covered"
      (is (empty? (handle-command (assoc return-command :returning? false, :covered? false)
                                  [territory-defined territory-assigned] injections))))

    (testing "can mark as covered multiple times, if the date is different"
      (is (= [(assoc territory-covered :assignment/covered-date (.plusDays end-date 1))]
             (handle-command (assoc only-mark-covered-command :date (.plusDays end-date 1))
                             [territory-defined territory-assigned territory-covered] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command return-command [territory-defined territory-assigned territory-covered territory-returned] injections))
          "returned & covered")
      (is (empty? (handle-command only-returning-command [territory-defined territory-assigned territory-returned] injections))
          "only returned")
      (is (empty? (handle-command only-mark-covered-command [territory-defined territory-assigned territory-covered] injections))
          "only covered"))

    (testing "cannot return if territory is not assigned"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-assignment #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000002\" #uuid \"00000000-0000-0000-0000-000000000003\"]]")
           (handle-command return-command [territory-defined] injections))))

    (testing "cannot return if assignment ID is wrong"
      (let [return-command (assoc return-command :assignment/id (UUID. 0 0x666))]
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:no-such-assignment #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000002\" #uuid \"00000000-0000-0000-0000-000000000666\"]]")
             (handle-command return-command [territory-defined territory-assigned] injections)))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:assign-territory cong-id territory-id publisher-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command return-command [territory-defined territory-assigned] injections)))))))
