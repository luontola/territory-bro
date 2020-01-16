;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user-test
  (:require [clojure.test :refer :all]
            [medley.core :refer [deep-merge dissoc-in]]
            [territory-bro.commands :as commands]
            [territory-bro.events :as events]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro ValidationException NoPermitException)))

(def cong-id (UUID. 1 0))
(def user-id (UUID. 2 0))
(def gis-user-created {:event/type :congregation.event/gis-user-created
                       :event/version 1
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "gis_user_0000000000000001_0000000000000002"
                       :gis-user/password "secret123"})
(def gis-user-deleted {:event/type :congregation.event/gis-user-deleted
                       :event/version 1
                       :congregation/id cong-id
                       :user/id user-id
                       :gis-user/username "gis_user_0000000000000001_0000000000000002"})

(defn- apply-events [events]
  (testutil/apply-events gis-user/gis-users-view events))

(defn- handle-command [command events injections]
  (->> (gis-user/handle-command (testutil/validate-command command)
                                (events/validate-events events)
                                injections)
       (events/validate-events)))

(deftest gis-users-view-test
  (testing "GIS user created"
    (let [events [gis-user-created]
          expected {::gis-user/gis-users
                    {cong-id {user-id {:user/id user-id
                                       :gis-user/username "gis_user_0000000000000001_0000000000000002"
                                       :gis-user/password "secret123"}}}}]
      (is (= expected (apply-events events)))

      (testing "> GIS user deleted"
        (let [events (conj events gis-user-deleted)
              expected {}]
          (is (= expected (apply-events events))))))))

(deftest create-gis-user-test
  (let [injections {:generate-password (constantly "secret123")
                    :db-user-exists? (constantly false)
                    :check-permit (fn [_permit])
                    :check-congregation-exists (fn [_cong-id])
                    :check-user-exists (fn [_user-id])}
        command {:command/type :gis-user.command/create-gis-user
                 :command/time (Instant/now)
                 :command/system "test"
                 :congregation/id cong-id
                 :user/id user-id}
        events []]

    (testing "valid command"
      (is (= [gis-user-created]
             (handle-command command events injections))))

    (testing "enforces unique usernames"
      (let [injections (assoc injections :db-user-exists? (fn [schema]
                                                            (contains? #{"gis_user_0000000000000001_0000000000000002"}
                                                                       schema)))]
        (is (= [(assoc gis-user-created :gis-user/username "gis_user_0000000000000001_0000000000000002_1")]
               (handle-command command events injections))))
      (let [injections (assoc injections :db-user-exists? (fn [schema]
                                                            (contains? #{"gis_user_0000000000000001_0000000000000002"
                                                                         "gis_user_0000000000000001_0000000000000002_1"
                                                                         "gis_user_0000000000000001_0000000000000002_2"
                                                                         "gis_user_0000000000000001_0000000000000002_3"}
                                                                       schema)))]
        (is (= [(assoc gis-user-created :gis-user/username "gis_user_0000000000000001_0000000000000002_4")]
               (handle-command command events injections)))))

    (testing "is idempotent"
      (is (empty? (handle-command command
                                  (conj events gis-user-created)
                                  injections))
          "already created"))

    (testing "recreating"
      (is (= [gis-user-created]
             (handle-command command
                             (conj events gis-user-created gis-user-deleted)
                             injections))))

    (testing "checks congregation exists"
      (let [injections (assoc injections
                              :check-congregation-exists (fn [id]
                                                           (is (= cong-id id))
                                                           (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException (handle-command command events injections)))))

    (testing "checks user exists"
      (let [injections (assoc injections
                              :check-user-exists (fn [id]
                                                   (is (= user-id id))
                                                   (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException (handle-command command events injections)))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:create-gis-user cong-id user-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command command events injections)))))))

(deftest delete-gis-user-test
  (let [injections {:check-permit (fn [_permit])
                    :check-congregation-exists (fn [_cong-id])
                    :check-user-exists (fn [_user-id])}
        command {:command/type :gis-user.command/delete-gis-user
                 :command/time (Instant/now)
                 :command/system "test"
                 :congregation/id cong-id
                 :user/id user-id}
        events [gis-user-created]]

    (testing "valid command"
      (is (= [gis-user-deleted]
             (handle-command command
                             events
                             injections))))

    (testing "username comes from the gis-user-created event and is not re-generated"
      (is (= [(assoc gis-user-deleted :gis-user/username "foo")]
             (handle-command command
                             (conj events (assoc gis-user-created :gis-user/username "foo"))
                             injections))))

    (testing "is idempotent"
      (is (empty? (handle-command command (drop-last events) injections))
          "not created")
      (is (empty? (handle-command command
                                  (conj events gis-user-deleted)
                                  injections))
          "already deleted"))

    (testing "checks congregation exists"
      (let [injections (assoc injections
                              :check-congregation-exists (fn [id]
                                                           (is (= cong-id id))
                                                           (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException (handle-command command events injections)))))

    (testing "checks user exists"
      (let [injections (assoc injections
                              :check-user-exists (fn [id]
                                                   (is (= user-id id))
                                                   (throw (ValidationException. [[:dummy]]))))]
        (is (thrown? ValidationException (handle-command command events injections)))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:delete-gis-user cong-id user-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command command events injections)))))))

(deftest generate-password-test
  (let [a (gis-user/generate-password 10)
        b (gis-user/generate-password 10)]
    (is (= 10 (count a) (count b)))
    (is (not= a b))))
