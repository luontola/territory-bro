;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation-test
  (:require [clojure.test :refer :all]
            [medley.core :refer [deep-merge]]
            [territory-bro.commands :as commands]
            [territory-bro.congregation :as congregation]
            [territory-bro.events :as events]
            [territory-bro.permissions :as permissions]
            [territory-bro.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException WriteConflictException)))

(defn- apply-events [events]
  (testutil/apply-events congregation/congregations-view events))

(defn- handle-command [command events injections]
  (->> (congregation/handle-command (testutil/validate-command command)
                                    (events/validate-events events)
                                    injections)
       (events/validate-events)))

(deftest congregations-view-test
  (testing "created"
    (let [cong-id (UUID. 0 1)
          events [{:event/type :congregation.event/congregation-created
                   :event/version 1
                   :congregation/id cong-id
                   :congregation/name "Cong1 Name"
                   :congregation/schema-name "cong1_schema"}]
          expected {::congregation/congregations
                    {cong-id {:congregation/id cong-id
                              :congregation/name "Cong1 Name"
                              :congregation/schema-name "cong1_schema"}}}]
      (is (= expected (apply-events events)))

      (testing "> permission granted"
        (let [user-id (UUID. 0 2)
              events (conj events {:event/type :congregation.event/permission-granted
                                   :event/version 1
                                   :congregation/id cong-id
                                   :user/id user-id
                                   :permission/id :view-congregation})
              expected (deep-merge expected
                                   {::congregation/congregations
                                    {cong-id {:congregation/user-permissions {user-id #{:view-congregation}}}}
                                    ::permissions/permissions
                                    {user-id {cong-id {:view-congregation true}}}})]
          (is (= expected (apply-events events)))

          (testing "> permissing revoked"
            (let [events (conj events {:event/type :congregation.event/permission-revoked
                                       :event/version 1
                                       :congregation/id cong-id
                                       :user/id user-id
                                       :permission/id :view-congregation})
                  expected (-> expected
                               (deep-merge {::congregation/congregations
                                            {cong-id {:congregation/user-permissions {user-id #{}}}}})
                               (dissoc ::permissions/permissions))]
              (is (= expected (apply-events events)))))))

      (testing "> congregation renamed"
        (let [events (conj events {:event/type :congregation.event/congregation-renamed
                                   :event/version 1
                                   :congregation/id cong-id
                                   :congregation/name "New Name"})
              expected (deep-merge expected
                                   {::congregation/congregations
                                    {cong-id {:congregation/name "New Name"}}})]
          (is (= expected (apply-events events))))))))

(deftest congregation-access-test
  (let [cong-id (UUID. 0 1)
        unrelated-cong-id (UUID. 0 2)
        user-id (UUID. 0 3)
        events [{:event/type :congregation.event/congregation-created
                 :event/version 1
                 :congregation/id cong-id
                 :congregation/name "Cong1 Name"
                 :congregation/schema-name "cong1_schema"}
                {:event/type :congregation.event/congregation-created
                 :event/version 1
                 :congregation/id unrelated-cong-id
                 :congregation/name "Cong2 Name"
                 :congregation/schema-name "cong2_schema"}]
        state (apply-events events)]

    (testing "cannot see congregations by default"
      (is (nil? (congregation/get-my-congregation state cong-id user-id)))
      (is (empty? (congregation/get-my-congregations state user-id))))

    (let [events (conj events {:event/type :congregation.event/permission-granted
                               :event/version 1
                               :congregation/id cong-id
                               :user/id user-id
                               :permission/id :view-congregation})
          state (apply-events events)]
      (testing "can see congregations after granting access"
        (is (= cong-id (:congregation/id (congregation/get-my-congregation state cong-id user-id))))
        (is (= [cong-id] (->> (congregation/get-my-congregations state user-id)
                              (map :congregation/id)))))

      (testing "list users"
        (is (= [user-id] (congregation/get-users state cong-id)))
        (is (empty? (congregation/get-users state unrelated-cong-id))
            "unrelated congregation"))

      (let [events (conj events {:event/type :congregation.event/permission-revoked
                                 :event/version 1
                                 :congregation/id cong-id
                                 :user/id user-id
                                 :permission/id :view-congregation})
            state (apply-events events)]
        (testing "cannot see congregations after revoking access"
          (is (nil? (congregation/get-my-congregation state cong-id user-id)))
          (is (empty? (congregation/get-my-congregations state user-id))))

        (testing "list users"
          (is (empty? (congregation/get-users state cong-id)))
          (is (empty? (congregation/get-users state unrelated-cong-id))
              "unrelated congregation"))))

    (testing "superadmin can access all congregations"))) ; TODO

(deftest check-congregation-exists-test
  (let [cong-id (UUID. 0 1)
        events [{:event/type :congregation.event/congregation-created
                 :event/version 1
                 :congregation/id cong-id
                 :congregation/name "Cong1 Name"
                 :congregation/schema-name "cong1_schema"}]
        state (apply-events events)]

    (testing "exists"
      (is (nil? (congregation/check-congregation-exists state cong-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (testutil/re-equals "[[:no-such-congregation #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (congregation/check-congregation-exists state (UUID. 0 0x666)))))))

;;; Commands

(deftest create-congregation-test
  (let [cong-id (UUID. 0 1)
        user-id (UUID. 0 2)
        injections {:generate-tenant-schema-name (fn [id]
                                                   (is (= cong-id id))
                                                   "cong_schema")
                    :check-event-stream-does-not-exist (fn [_id])}
        create-command {:command/type :congregation.command/create-congregation
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :congregation/name "the name"}
        created-event {:event/type :congregation.event/congregation-created
                       :event/version 1
                       :congregation/id cong-id
                       :congregation/name "the name"
                       :congregation/schema-name "cong_schema"}
        view-permission-granted {:event/type :congregation.event/permission-granted
                                 :event/version 1
                                 :congregation/id cong-id
                                 :user/id user-id
                                 :permission/id :view-congregation}
        configure-permission-granted {:event/type :congregation.event/permission-granted
                                      :event/version 1
                                      :congregation/id cong-id
                                      :user/id user-id
                                      :permission/id :configure-congregation}
        gis-permission-granted {:event/type :congregation.event/permission-granted
                                :event/version 1
                                :congregation/id cong-id
                                :user/id user-id
                                :permission/id :gis-access}]
    (testing "created"
      (is (= [created-event
              view-permission-granted
              configure-permission-granted
              gis-permission-granted]
             (handle-command create-command [] injections))))

    (testing "error: name is blank"
      (let [command (assoc create-command :congregation/name "   ")]
        (is (thrown-with-msg?
             ValidationException (testutil/re-equals "[[:missing-name]]")
             (handle-command command [] injections)))))

    (let [injections (assoc injections :check-event-stream-does-not-exist (fn [id]
                                                                            (is (= cong-id id))
                                                                            (throw (WriteConflictException.))))]
      (testing "create is idempotent"
        (is (empty? (handle-command create-command [created-event] injections))))

      (testing "stream ID conflicts with another entity"
        (is (thrown? WriteConflictException
                     (handle-command create-command [] injections)))))))

(deftest rename-congregation-test
  (let [cong-id (UUID. 0 1)
        user-id (UUID. 0 2)
        injections {:check-permit (fn [_permit])}
        created-event {:event/type :congregation.event/congregation-created
                       :event/version 1
                       :congregation/id cong-id
                       :congregation/name "old name"
                       :congregation/schema-name ""}
        rename-command {:command/type :congregation.command/rename-congregation
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :congregation/name "new name"}
        renamed-event {:event/type :congregation.event/congregation-renamed
                       :event/version 1
                       :congregation/id cong-id
                       :congregation/name "new name"}]

    (testing "name changed"
      (is (= [renamed-event]
             (handle-command rename-command [created-event] injections))))

    (testing "name not changed"
      (testing "from original"
        (let [command (assoc rename-command :congregation/name "old name")]
          (is (empty? (handle-command command [created-event] injections)))))

      (testing "from previous rename"
        (is (empty? (handle-command rename-command [created-event renamed-event] injections)))))

    (testing "error: name is blank"
      (let [command (assoc rename-command :congregation/name "   ")]
        (is (thrown-with-msg?
             ValidationException (testutil/re-equals "[[:missing-name]]")
             (handle-command command [created-event] injections)))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:configure-congregation cong-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command rename-command [created-event] injections)))))))

(deftest add-user-to-congregation-test
  (let [cong-id (UUID. 0 1)
        admin-id (UUID. 0 2)
        new-user-id (UUID. 0 3)
        invalid-user-id (UUID. 0 4)
        injections {:check-permit (fn [_permit])
                    :user-exists? (fn [user-id]
                                    (= new-user-id user-id))}
        created-event {:event/type :congregation.event/congregation-created
                       :event/version 1
                       :congregation/id cong-id
                       :congregation/name ""
                       :congregation/schema-name ""}
        add-user-command {:command/type :congregation.command/add-user
                          :command/time (Instant/now)
                          :command/user admin-id
                          :congregation/id cong-id
                          :user/id new-user-id}
        view-granted {:event/type :congregation.event/permission-granted
                      :event/version 1
                      :congregation/id cong-id
                      :user/id new-user-id
                      :permission/id :view-congregation}
        configure-granted {:event/type :congregation.event/permission-granted
                           :event/version 1
                           :congregation/id cong-id
                           :user/id new-user-id
                           :permission/id :configure-congregation}
        gis-access-granted {:event/type :congregation.event/permission-granted
                            :event/version 1
                            :congregation/id cong-id
                            :user/id new-user-id
                            :permission/id :gis-access}]

    (testing "user added"
      (is (= [view-granted configure-granted gis-access-granted]
             (handle-command add-user-command [created-event] injections))))

    (testing "user already in congregation"
      (is (empty? (handle-command add-user-command [created-event view-granted] injections))))

    (testing "user doesn't exist"
      (let [invalid-command (assoc add-user-command
                                   :user/id invalid-user-id)]
        (is (thrown-with-msg? ValidationException (testutil/re-equals "[[:no-such-user #uuid \"00000000-0000-0000-0000-000000000004\"]]")
                              (handle-command invalid-command [created-event] injections)))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:configure-congregation cong-id] permit))
                                              (throw (NoPermitException. nil nil))))]
        (is (thrown? NoPermitException
                     (handle-command add-user-command [created-event] injections)))))))

(deftest set-user-permissions-test
  (let [cong-id (UUID. 0 1)
        admin-id (UUID. 0 2)
        target-user-id (UUID. 0 3)
        invalid-user-id (UUID. 0 4)
        injections {:check-permit (fn [_permit])
                    :user-exists? (fn [user-id]
                                    (= target-user-id user-id))}
        created-event {:event/type :congregation.event/congregation-created
                       :event/version 1
                       :congregation/id cong-id
                       :congregation/name ""
                       :congregation/schema-name ""}
        permission-granted-event {:event/type :congregation.event/permission-granted
                                  :event/version 1
                                  :congregation/id cong-id
                                  :user/id target-user-id
                                  :permission/id :PLACEHOLDER}
        permission-revoked-event {:event/type :congregation.event/permission-revoked
                                  :event/version 1
                                  :congregation/id cong-id
                                  :user/id target-user-id
                                  :permission/id :PLACEHOLDER}
        set-user-permissions-command {:command/type :congregation.command/set-user-permissions
                                      :command/time (Instant/now)
                                      :command/user admin-id
                                      :congregation/id cong-id
                                      :user/id target-user-id
                                      :permission/ids []}]

    (testing "grant permissions"
      (let [given [created-event
                   (assoc permission-granted-event :permission/id :view-congregation)]
            when (assoc set-user-permissions-command :permission/ids [:view-congregation :configure-congregation])
            then [(assoc permission-granted-event :permission/id :configure-congregation)]]
        (is (= then (handle-command when given injections)))))

    (testing "revoke permissions"
      (let [given [created-event
                   (assoc permission-granted-event :permission/id :view-congregation)
                   (assoc permission-granted-event :permission/id :configure-congregation)]
            when (assoc set-user-permissions-command :permission/ids [:view-congregation])
            then [(assoc permission-revoked-event :permission/id :configure-congregation)]]
        (is (= then (handle-command when given injections)))))

    (testing "setting permissions is idempotent"
      (let [given [created-event
                   (assoc permission-granted-event :permission/id :view-congregation)]
            when (assoc set-user-permissions-command :permission/ids [:view-congregation])
            then []]
        (is (= then (handle-command when given injections)))))

    (testing "remove user from congregation"
      (let [given [created-event
                   (assoc permission-granted-event :permission/id :view-congregation)]
            when (assoc set-user-permissions-command :permission/ids [])
            then [(assoc permission-revoked-event :permission/id :view-congregation)]]
        (is (= then (handle-command when given injections)))))

    (testing "user not in congregation") ;; TODO: should this command disallow adding new users?

    (testing "user doesn't exist"
      (let [given [created-event]
            when (assoc set-user-permissions-command
                        :user/id invalid-user-id
                        :permission/ids [:view-congregation])]
        (is (thrown-with-msg? ValidationException (testutil/re-equals "[[:no-such-user #uuid \"00000000-0000-0000-0000-000000000004\"]]")
                              (handle-command when given injections)))))

    (testing "checks permits"
      (let [injections (assoc injections
                              :check-permit (fn [permit]
                                              (is (= [:configure-congregation cong-id] permit))
                                              (throw (NoPermitException. nil nil))))
            given [created-event]
            when (assoc set-user-permissions-command :permission/ids [:view-congregation])]
        (is (thrown? NoPermitException
                     (handle-command when given injections)))))))
