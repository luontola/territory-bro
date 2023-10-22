;; Copyright © 2015-2023 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.share-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.share :as share]
            [territory-bro.events :as events]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.test.testutil :as testutil :refer [re-equals]])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException WriteConflictException)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def territory-id (UUID. 0 3))
(def territory-id2 (UUID. 0 4))
(def share-id (UUID. 0 0x10))
(def share-id2 (UUID. 0 0x20))
(def share-key "abc123")
(def share-key2 "def456")
(def test-time (Instant/ofEpochSecond 42))

(def share-created
  {:event/type :share.event/share-created
   :share/id share-id
   :share/key share-key
   :share/type :link
   :congregation/id cong-id
   :territory/id territory-id})
(def share-created2
  (assoc share-created
         :share/id share-id2
         :share/key share-key2
         :territory/id territory-id2))
(def share-opened
  {:event/type :share.event/share-opened
   :event/time test-time
   :share/id share-id})

(defn- apply-events [events]
  (testutil/apply-events share/projection events))

(defn- handle-command [command events injections]
  (->> (share/handle-command (testutil/validate-command command)
                             (apply-events (events/validate-events events))
                             injections)
       (events/validate-events)))


;;;; Projection

(deftest share-projection-test
  (testing "created"
    (let [events [share-created]
          expected {::share/share-keys {share-key share-id}
                    ::share/shares {share-id {:share/id share-id
                                              :congregation/id cong-id
                                              :territory/id territory-id}}}]
      (is (= expected (apply-events events)))

      (testing "> opened"
        (let [events (conj events share-opened)
              expected (assoc-in expected [::share/shares share-id :share/last-opened] test-time)]
          (is (= expected (apply-events events))))))))

(deftest grant-opened-shares-test
  (let [state (apply-events [share-created
                             share-created2])
        permit1 [:view-territory cong-id territory-id]
        permit2 [:view-territory cong-id territory-id2]]
    (testing "no shares opened"
      (is (not (permissions/allowed? state user-id permit1)))
      (is (not (permissions/allowed? state user-id permit2))))

    (testing "one share opened"
      (let [state (share/grant-opened-shares state [share-id] user-id)]
        (is (permissions/allowed? state user-id permit1))
        (is (not (permissions/allowed? state user-id permit2)))))

    (testing "many shares opened"
      (let [state (share/grant-opened-shares state [share-id share-id2] user-id)]
        (is (permissions/allowed? state user-id permit1))
        (is (permissions/allowed? state user-id permit2))))))


;;;; Queries

(deftest check-share-exists-test
  (let [state (apply-events [share-created])]

    (testing "exists"
      (is (nil? (share/check-share-exists state share-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-share #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (share/check-share-exists state (UUID. 0 0x666)))))))

(deftest find-share-by-key-test
  (let [state (apply-events [share-created])]
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

(deftest demo-share-key-test
  (testing "key format"
    (is (= "demo-aOiskx9fR0G6smZfLDOOVg"
           (share/demo-share-key #uuid "68e8ac93-1f5f-4741-bab2-665f2c338e56"))))

  (testing "keys can be converted back to UUIDs"
    (let [uuid (UUID/randomUUID)
          key (share/demo-share-key uuid)]
      (is (= uuid (share/parse-demo-share-key key)))))

  (testing "cannot parse invalid demo share keys"
    (is (nil? (share/parse-demo-share-key nil))
        "nil")
    (is (nil? (share/parse-demo-share-key ""))
        "empty string")
    (is (nil? (share/parse-demo-share-key "xxxx-aOiskx9fR0G6smZfLDOOVg"))
        "wrong prefix")
    (is (nil? (share/parse-demo-share-key "demo-aOiskx9fR0G6smZfLDOOVg!"))
        "wrong suffix - invalid base64 string")
    (is (nil? (share/parse-demo-share-key "demo-aOiskx9fR0G6smZfLDOO"))
        "wrong suffix - wrong number of decoded bytes")))

(deftest create-share-test
  (let [injections {:check-permit (fn [_permit])}
        create-command {:command/type :share.command/create-share
                        :command/time (Instant/now)
                        :command/user user-id
                        :share/id share-id
                        :share/key share-key
                        :share/type :link
                        :congregation/id cong-id
                        :territory/id territory-id}]

    (testing "created"
      (is (= [share-created]
             (handle-command create-command [] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command create-command [share-created] injections))))

    (testing "checks share key uniqueness"
      ;; trying to create a new share (i.e. new share ID) with the same old share key
      (let [conflicting-command (assoc create-command :share/id (UUID/randomUUID))]
        (is (thrown-with-msg?
             WriteConflictException (re-equals "share key abc123 already in use by share 00000000-0000-0000-0000-000000000010")
             (handle-command conflicting-command [share-created] injections)))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:share-territory-link cong-id territory-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command create-command [] injections)))))))

(deftest record-share-opened-test
  (let [injections {}
        anonymous-command {:command/type :share.command/record-share-opened
                           :command/time (Instant/now)
                           :share/id share-id}
        user-command (assoc anonymous-command :command/user user-id)]
    (is (= [{:event/type :share.event/share-opened
             :share/id share-id}]
           (handle-command anonymous-command [share-created] injections)
           (handle-command user-command [share-created] injections)))))
