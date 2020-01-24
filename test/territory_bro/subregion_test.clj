;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.subregion-test
  (:require [clojure.test :refer :all]
            [medley.core :refer [deep-merge]]
            [territory-bro.events :as events]
            [territory-bro.subregion :as subregion]
            [territory-bro.testdata :as testdata]
            [territory-bro.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException)))

(def cong-id (UUID. 0 1))
(def subregion-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def subregion-defined {:event/type :subregion.event/subregion-defined
                        :event/version 1
                        :congregation/id cong-id
                        :subregion/id subregion-id
                        :subregion/name "the name"
                        :subregion/location testdata/wkt-multi-polygon})

(defn- apply-events [events]
  (testutil/apply-events subregion/projection events))

(defn- handle-command [command events injections]
  (->> (subregion/handle-command (testutil/validate-command command)
                                 (events/validate-events events)
                                 injections)
       (events/validate-events)))

(deftest subregion-projection-test
  (testing "created"
    (let [events [subregion-defined]
          expected {::subregion/subregions
                    {cong-id {subregion-id {:subregion/id subregion-id
                                            :subregion/name "the name"
                                            :subregion/location testdata/wkt-multi-polygon}}}}]
      (is (= expected (apply-events events)))

      (testing "> updated"
        (let [events (conj events (assoc subregion-defined
                                         :subregion/name "new name"
                                         :subregion/location "new location"))
              expected (deep-merge expected
                                   {::subregion/subregions
                                    {cong-id {subregion-id {:subregion/name "new name"
                                                            :subregion/location "new location"}}}})]
          (is (= expected (apply-events events))))))))

(deftest create-subregion-test
  (let [injections {:check-permit (fn [_permit])}
        create-command {:command/type :subregion.command/create-subregion
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :subregion/id subregion-id
                        :subregion/name "the name"
                        :subregion/location testdata/wkt-multi-polygon}]

    (testing "created"
      (is (= [subregion-defined]
             (handle-command create-command [] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:define-subregion cong-id subregion-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command create-command [] injections)))))))

(deftest update-subregion-test
  (let [injections {:check-permit (fn [_permit])}
        update-command {:command/type :subregion.command/update-subregion
                        :command/time (Instant/now)
                        :command/user user-id
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

    (testing "nothing changed"
      (is (empty? (handle-command update-command [subregion-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:define-subregion cong-id subregion-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command update-command [] injections)))))))
