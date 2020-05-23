;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.share-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.share :as share]
            [territory-bro.events :as events]
            [territory-bro.test.testutil :as testutil :refer [re-equals]])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException WriteConflictException)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def territory-id (UUID. 0 3))
(def share-id (UUID. 0 4))
(def share-key "abc123")

(def link-share-created
  {:event/type :share.event/share-created
   :share/id share-id
   :share/key share-key
   :share/type :link
   :congregation/id cong-id
   :territory/id territory-id})

(defn- apply-events [events]
  (testutil/apply-events share/projection events))

(defn- handle-command [command events injections]
  (->> (share/handle-command (testutil/validate-command command)
                             (events/validate-events events)
                             (assoc injections :state (apply-events events)))
       (events/validate-events)))


;;;; Projection

(deftest share-projection-test
  (testing "created"
    (let [events [link-share-created]
          expected {::share/share-keys {share-key share-id}
                    ::share/shares {share-id {:share/id share-id
                                              :congregation/id cong-id
                                              :territory/id territory-id}}}]
      (is (= expected (apply-events events))))))


;;;; Queries

(deftest find-share-by-key-test
  (let [state (apply-events [link-share-created])]
    (testing "existing share"
      (is (= {:share/id share-id
              :congregation/id cong-id
              :territory/id territory-id}
             (share/find-share-by-key state share-key))))

    (testing "invalid share key"
      (is (nil? (share/find-share-by-key state "foo"))))))


;;;; Commands

(deftest generate-share-key-test
  (testing "key format"
    (let [key (share/generate-share-key)]
      (is (string? key))
      (is (re-matches #"[a-zA-Z0-9_-]{11}" key))))

  (testing "generates unique keys"
    (let [keys (repeatedly 10 share/generate-share-key)]
      (is (= (distinct keys) keys)))))

(deftest share-territory-link-test
  (let [injections {:check-permit (fn [_permit])}
        create-command {:command/type :share.command/share-territory-link
                        :command/time (Instant/now)
                        :command/user user-id
                        :share/id share-id
                        :share/key share-key
                        :congregation/id cong-id
                        :territory/id territory-id}]

    (testing "created"
      (is (= [link-share-created]
             (handle-command create-command [] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command create-command [link-share-created] injections))))

    (testing "checks share key uniqueness"
      ;; trying to create a new share (i.e. new share ID) with the same old share key
      (let [conflicting-command (assoc create-command :share/id (UUID/randomUUID))]
        (is (thrown-with-msg?
             WriteConflictException (re-equals "share key abc123 already in use by share 00000000-0000-0000-0000-000000000004")
             (handle-command conflicting-command [link-share-created] injections)))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:share-territory-link cong-id territory-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command create-command [] injections)))))))
