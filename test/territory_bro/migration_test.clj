;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.migration-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.migration :as migration]
            [territory-bro.projections :as projections]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def test-time (Instant/ofEpochSecond 42))

(def congregation-created
  {:event/type :congregation.event/congregation-created
   :congregation/id cong-id
   :congregation/name "Cong1 Name"
   :congregation/schema-name "cong1_schema"})

(def admin-permissions-granted
  (congregation/admin-permissions-granted cong-id user-id))
(def non-admin-permissions-granted
  (remove #(= :configure-congregation (:permission/id %)) admin-permissions-granted))

(defn- apply-events [events]
  (testutil/apply-events projections/projection events))

(defn- generate-commands [events]
  (->> (migration/generate-commands (apply-events events))
       (testutil/validate-commands)))

(use-fixtures :once (fixed-clock-fixture test-time))


(deftest add-missing-admin-permissions-test
  (testing "non-admin users"
    (is (empty? (generate-commands (concat [congregation-created]
                                           non-admin-permissions-granted)))))

  (testing "admin has full permissions"
    (is (empty? (generate-commands (concat [congregation-created]
                                           admin-permissions-granted)))))

  (testing "admin is missing some permissions"
    (is (= [{:command/type :congregation.command/set-user-permissions
             :command/time test-time
             :command/system "territory-bro.migration"
             :congregation/id cong-id
             :user/id user-id
             :permission/ids congregation/all-permissions}]
           (generate-commands (concat [congregation-created]
                                      (drop-last admin-permissions-granted)))))))
