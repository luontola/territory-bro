;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.gis-user-process-test
  (:require [clojure.test :refer :all]
            [territory-bro.gis.gis-user-process :as gis-user-process]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def test-time (Instant/ofEpochSecond 42))

(defn- apply-events [events]
  (testutil/apply-events gis-user-process/projection events))

(defn- generate-commands [events]
  (->> (gis-user-process/generate-commands (apply-events events))
       (testutil/validate-commands)))

(use-fixtures :once (fixed-clock-fixture test-time))


(deftest generate-commands-test
  (let [events []]
    (is (empty? (generate-commands events)))

    (testing "GIS permission granted"
      (let [events (conj events {:event/type :congregation.event/permission-granted
                                 :congregation/id cong-id
                                 :user/id user-id
                                 :permission/id :gis-access})]
        (is (= [{:command/type :gis-user.command/create-gis-user
                 :command/time test-time
                 :command/system "territory-bro.gis.gis-user-process"
                 :congregation/id cong-id
                 :user/id user-id}]
               (generate-commands events)))

        (testing "> GIS user created"
          (let [events (conj events {:event/type :congregation.event/gis-user-created
                                     :congregation/id cong-id
                                     :user/id user-id
                                     :gis-user/username "username123"
                                     :gis-user/password "password123"})]
            (is (empty? (generate-commands events)))

            (testing "> GIS permission revoked"
              (let [events (conj events {:event/type :congregation.event/permission-revoked
                                         :congregation/id cong-id
                                         :user/id user-id
                                         :permission/id :gis-access})]
                (is (= [{:command/type :gis-user.command/delete-gis-user
                         :command/time test-time
                         :command/system "territory-bro.gis.gis-user-process"
                         :congregation/id cong-id
                         :user/id user-id}]
                       (generate-commands events)))

                (testing "> GIS user deleted"
                  (let [events (conj events {:event/type :congregation.event/gis-user-deleted
                                             :congregation/id cong-id
                                             :user/id user-id
                                             :gis-user/username "username123"})]
                    (is (empty? (generate-commands events)))))))))

        (testing "> GIS permission revoked"
          (let [events (conj events {:event/type :congregation.event/permission-revoked
                                     :congregation/id cong-id
                                     :user/id user-id
                                     :permission/id :gis-access})]
            (is (empty? (generate-commands events)))))

        (testing "> unrelated permission revoked"
          (let [events (conj events {:event/type :congregation.event/permission-revoked
                                     :congregation/id cong-id
                                     :user/id user-id
                                     :permission/id :view-congregation})]
            (is (= [{:command/type :gis-user.command/create-gis-user
                     :command/time test-time
                     :command/system "territory-bro.gis.gis-user-process"
                     :congregation/id cong-id
                     :user/id user-id}]
                   (generate-commands events)))))))

    (testing "unrelated permission granted"
      (let [events (conj events {:event/type :congregation.event/permission-granted
                                 :congregation/id cong-id
                                 :user/id user-id
                                 :permission/id :view-congregation})]
        (is (empty? (generate-commands events)))))))
