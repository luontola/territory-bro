;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user-process-test
  (:require [clojure.test :refer :all]
            [territory-bro.gis-user-process :as gis-user-process]
            [territory-bro.testutil :as testutil]
            [territory-bro.events :as events])
  (:import (java.time Instant)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def test-time (Instant/ofEpochSecond 42))

(defn- apply-events [events]
  (testutil/apply-events gis-user-process/projection events))

(defn- generate-commands [events]
  (-> events
      ; TODO: validate events (need strict and lenient validation to avoid need for generic event fields in all tests)
      ;(events/validate-events)
      (apply-events)
      (gis-user-process/generate-commands {:now (fn [] test-time)})))

(deftest generate-commands-test
  (let [events []]
    (is (empty? (generate-commands events)))

    (testing "GIS permission granted"
      (let [events (conj events {:event/type :congregation.event/permission-granted
                                 :event/version 1
                                 :congregation/id cong-id
                                 :user/id user-id
                                 :permission/id :gis-access})]
        (is (= [{:command/type :gis-user.command/create-gis-user
                 :command/time test-time
                 :command/system "territory-bro.gis-user-process"
                 :congregation/id cong-id
                 :user/id user-id}]
               (generate-commands events)))

        (testing "> GIS user created"
          (let [events (conj events {:event/type :congregation.event/gis-user-created
                                     :event/version 1
                                     :congregation/id cong-id
                                     :user/id user-id
                                     :gis-user/username "username123"
                                     :gis-user/password "password123"})]
            (is (empty? (generate-commands events)))

            (testing "> GIS permission revoked"
              (let [events (conj events {:event/type :congregation.event/permission-revoked
                                         :event/version 1
                                         :congregation/id cong-id
                                         :user/id user-id
                                         :permission/id :gis-access})]
                (is (= [{:command/type :gis-user.command/delete-gis-user
                         :command/time test-time
                         :command/system "territory-bro.gis-user-process"
                         :congregation/id cong-id
                         :user/id user-id}]
                       (generate-commands events)))

                (testing "> GIS user deleted"
                  (let [events (conj events {:event/type :congregation.event/gis-user-deleted
                                             :event/version 1
                                             :congregation/id cong-id
                                             :user/id user-id
                                             :gis-user/username "username123"})]
                    (is (empty? (generate-commands events)))))))))

        (testing "> GIS permission revoked"
          (let [events (conj events {:event/type :congregation.event/permission-revoked
                                     :event/version 1
                                     :congregation/id cong-id
                                     :user/id user-id
                                     :permission/id :gis-access})]
            (is (empty? (generate-commands events)))))

        (testing "> unrelated permission revoked"
          (let [events (conj events {:event/type :congregation.event/permission-revoked
                                     :event/version 1
                                     :congregation/id cong-id
                                     :user/id user-id
                                     :permission/id :view-congregation})]
            (is (= [{:command/type :gis-user.command/create-gis-user
                     :command/time test-time
                     :command/system "territory-bro.gis-user-process"
                     :congregation/id cong-id
                     :user/id user-id}]
                   (generate-commands events)))))))

    (testing "unrelated permission granted"
      (let [events (conj events {:event/type :congregation.event/permission-granted
                                 :event/version 1
                                 :congregation/id cong-id
                                 :user/id user-id
                                 :permission/id :view-congregation})]
        (is (empty? (generate-commands events)))))))
