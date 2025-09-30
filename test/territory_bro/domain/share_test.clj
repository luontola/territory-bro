(ns territory-bro.domain.share-test
  (:require [clojure.test :refer :all]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.domain.share :as share]
            [territory-bro.events :as events]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.event-store :as event-store]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.infra.user :as user]
            [territory-bro.projections :as projections]
            [territory-bro.test.testutil :as testutil :refer [re-equals thrown-with-msg? thrown?]])
  (:import (java.time Instant LocalDate)
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
(def time-t0 (Instant/ofEpochSecond 0))
(def time-t1 (Instant/ofEpochSecond 1))
(def time-t2 (Instant/ofEpochSecond 2))

(def share-created
  {:event/type :share.event/share-created
   :event/time time-t1
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
   :event/time time-t1
   :share/id share-id})

(def territory-returned-first
  {:event/type :territory.event/territory-returned
   :event/time time-t0
   :congregation/id cong-id
   :territory/id territory-id
   :assignment/id (UUID/randomUUID)
   :assignment/end-date (LocalDate/of 2000 1 1)})
(def territory-returned-last
  (assoc territory-returned-first :event/time time-t2))

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
                                              :share/type :link
                                              :share/created time-t1
                                              :congregation/id cong-id
                                              :territory/id territory-id}}}]
      (is (= expected (apply-events events)))

      (testing "> opened"
        (let [events (conj events share-opened)
              expected (assoc-in expected [::share/shares share-id :share/last-opened] time-t1)]
          (is (= expected (apply-events events)))))

      (testing "> territory returned"
        (let [events (conj events territory-returned-last)
              expected (assoc-in expected [::share/territory-last-returned territory-id] time-t2)]
          (is (= expected (apply-events events))))))))

(deftest grant-opened-shares-test
  (let [state (apply-events [share-created
                             share-created2])
        permit-cong [:view-congregation cong-id]
        permit-cong-temp [:view-congregation-temporarily cong-id]
        permit-t1 [:view-territory cong-id territory-id]
        permit-t2 [:view-territory cong-id territory-id2]]
    (testing "no shares opened"
      (is (not (permissions/allowed? state user-id permit-cong)))
      (is (not (permissions/allowed? state user-id permit-cong-temp)))
      (is (not (permissions/allowed? state user-id permit-t1)))
      (is (not (permissions/allowed? state user-id permit-t2))))

    (testing "one share opened"
      (let [state (share/grant-opened-shares state [share-id] user-id)]
        (is (not (permissions/allowed? state user-id permit-cong)))
        (is (permissions/allowed? state user-id permit-cong-temp))
        (is (permissions/allowed? state user-id permit-t1))
        (is (not (permissions/allowed? state user-id permit-t2)))))

    (testing "many shares opened"
      (let [state (share/grant-opened-shares state [share-id share-id2] user-id)]
        (is (not (permissions/allowed? state user-id permit-cong)))
        (is (permissions/allowed? state user-id permit-cong-temp))
        (is (permissions/allowed? state user-id permit-t1))
        (is (permissions/allowed? state user-id permit-t2))))

    (testing "if the user already has the :view-congregation permission, will not grant also :view-congregation-temporarily"
      (let [state (permissions/grant state user-id permit-cong)]
        (is (permissions/allowed? state user-id permit-cong))
        (is (not (permissions/allowed? state user-id permit-cong-temp)))
        (is (not (permissions/allowed? state user-id permit-t1)))
        (is (not (permissions/allowed? state user-id permit-t2)))))

    (testing "non-existing shares are ignored silently"
      (is (= state (share/grant-opened-shares state [(UUID. 0 0x666)] user-id))))))


;;;; Queries

(deftest check-share-exists-test
  (let [state (apply-events [share-created])]

    (testing "exists"
      (is (nil? (share/check-share-exists state share-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-share #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (share/check-share-exists state (UUID. 0 0x666)))))))

(deftest find-valid-share-by-key-test
  (let [state (apply-events [share-created])]
    (testing "existing share"
      (is (= {:share/id share-id
              :share/type :link
              :share/created time-t1
              :congregation/id cong-id
              :territory/id territory-id}
             (share/find-valid-share-by-key state share-key))))

    (testing "invalid share key"
      (is (nil? (share/find-valid-share-by-key state "foo")))))

  (testing "share created, territory returned -> share is expired"
    (let [state (apply-events [share-created territory-returned-last])]
      (is (nil? (share/find-valid-share-by-key state share-key)))))

  (testing "territory returned, share created -> share is still valid"
    (let [state (apply-events [territory-returned-first share-created])]
      (is (some? (share/find-valid-share-by-key state share-key)))))

  (testing "QR code shares don't expire when the territory is returned"
    (let [state (apply-events [territory-returned-first
                               (assoc share-created :share/type :qr-code)
                               territory-returned-last])]
      (is (some? (share/find-valid-share-by-key state share-key))))))


;;;; Commands

(deftest generate-share-key-test
  (testing "key format"
    (let [key (share/generate-share-key)]
      (is (string? key))
      (is (re-matches #"[a-zA-Z0-9_-]{11}" key))))

  (testing "generates unique keys"
    (let [keys (repeatedly 10 share/generate-share-key)]
      (is (= (distinct keys) keys)))))

(def test-env
  {:public-url "https://example.com"
   :qr-code-base-url "https://qr.example.com"})

(deftest build-share-url-test
  (binding [config/env test-env]
    (testing "contains public URL, share key and territory number"
      (is (= "https://example.com/share/key/123" (share/build-share-url "key" "123"))))

    (testing "territory number is sanitized"
      (is (= "https://example.com/share/key/1-2" (share/build-share-url "key" "1-2"))
          "dash is an URL safe character")
      (is (= "https://example.com/share/key/1_2" (share/build-share-url "key" "1/2"))
          "sanitize special character")
      (is (= "https://example.com/share/key/1_2" (share/build-share-url "key" "1 2"))
          "sanitize space (URL encodes as '+')")
      (is (= "https://example.com/share/key/1_2" (share/build-share-url "key" "1, 2"))
          "join multiple consecutive sanitized characters "))))

(deftest build-qr-code-url-test
  (binding [config/env test-env]
    (testing "QR codes use a different subdomain and shorter URL path"
      (is (= "https://qr.example.com/key" (share/build-qr-code-url "key"))))))

(deftest demo-share-key-test
  (testing "key format"
    (is (= "demo-aOiskx9fR0G6smZfLDOOVg"
           (share/demo-share-key #uuid "68e8ac93-1f5f-4741-bab2-665f2c338e56"))))

  (testing "keys can be converted back to UUIDs"
    (let [uuid (random-uuid)
          key (share/demo-share-key uuid)]
      (is (= uuid (share/demo-share-key->territory-id key)))))

  (testing "cannot parse invalid demo share keys"
    (is (nil? (share/demo-share-key->territory-id nil))
        "nil")
    (is (nil? (share/demo-share-key->territory-id ""))
        "empty string")
    (is (nil? (share/demo-share-key->territory-id "xxxx-aOiskx9fR0G6smZfLDOOVg"))
        "wrong prefix")
    (is (nil? (share/demo-share-key->territory-id "demo-aOiskx9fR0G6smZfLDOOVg!"))
        "wrong suffix - invalid base64 string")
    (is (nil? (share/demo-share-key->territory-id "demo-aOiskx9fR0G6smZfLDOO"))
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
      (is (= [(dissoc share-created :event/time)] ; time is enriched by the dispatcher, not the command handler
             (handle-command create-command [] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command create-command [share-created] injections))))

    (testing "checks share key uniqueness"
      ;; trying to create a new share (i.e. new share ID) with the same old share key
      (let [conflicting-command (assoc create-command :share/id (random-uuid))]
        (is (thrown-with-msg?
             WriteConflictException (re-equals "share key abc123 already in use by share 00000000-0000-0000-0000-000000000010")
             (handle-command conflicting-command [share-created] injections)))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:share-territory-link cong-id territory-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command create-command [] injections)))))

    (testing "the share's territory must belong to the specified congregation, to prevent insecure direct object references"
      (let [cong-id2 (UUID. 0 0x6661)
            territory-id2 (UUID. 0 0x6662)
            bad-command (assoc create-command :territory/id territory-id2)
            events [(assoc dmz-test/congregation-created
                           :congregation/id cong-id)
                    (assoc dmz-test/territory-defined
                           :congregation/id cong-id
                           :territory/id territory-id)
                    (assoc dmz-test/congregation-created
                           :congregation/id cong-id2)
                    (assoc dmz-test/territory-defined
                           :congregation/id cong-id2
                           :territory/id territory-id2)]
            state (testutil/apply-events projections/projection events)]
        (binding [event-store/check-new-stream (constantly nil)
                  user/check-user-exists (constantly nil)]
          (is (thrown-with-msg?
               ValidationException (re-equals "[[:no-such-territory #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000006662\"]]")
               (dispatcher/validate-command bad-command nil state))))))))

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
