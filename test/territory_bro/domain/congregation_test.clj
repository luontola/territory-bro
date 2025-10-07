(ns territory-bro.domain.congregation-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.events :as events]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.test.testutil :as testutil :refer [re-equals replace-in thrown-with-msg? thrown?]])
  (:import (java.time Instant ZoneOffset)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException)))

(defn- apply-events [events]
  (testutil/apply-events congregation/projection events))

(defn- handle-command [command events injections]
  (->> (congregation/handle-command (testutil/validate-command command)
                                    (events/validate-events events)
                                    injections)
       (events/validate-events)))

(deftest congregation-projection-test
  (testing "created"
    (let [cong-id (UUID. 0 1)
          events [{:event/type :congregation.event/congregation-created
                   :congregation/id cong-id
                   :congregation/name "Cong1 Name"
                   :congregation/schema-name "cong1_schema"}]
          expected {::congregation/congregations
                    {cong-id {:congregation/id cong-id
                              :congregation/name "Cong1 Name"
                              :congregation/schema-name "cong1_schema"
                              :congregation/timezone ZoneOffset/UTC
                              :congregation/expire-shared-links-on-return true}}}]
      (is (= expected (apply-events events)))

      (testing "> view permission granted"
        (let [user-id (UUID. 0 2)
              events (conj events {:event/type :congregation.event/permission-granted
                                   :congregation/id cong-id
                                   :user/id user-id
                                   :permission/id :view-congregation})
              expected (-> expected
                           (assoc-in [::congregation/congregations cong-id :congregation/user-permissions user-id]
                                     #{:view-congregation})
                           (assoc-in [::permissions/permissions user-id cong-id]
                                     {:view-congregation true}))]
          (is (= expected (apply-events events)))

          (testing "> view permission revoked"
            (let [events (conj events {:event/type :congregation.event/permission-revoked
                                       :congregation/id cong-id
                                       :user/id user-id
                                       :permission/id :view-congregation})
                  expected (-> expected
                               (assoc-in [::congregation/congregations cong-id :congregation/user-permissions]
                                         {})
                               (dissoc ::permissions/permissions))]
              (is (= expected (apply-events events)))))

          (testing "> another permission granted"
            (let [events (conj events {:event/type :congregation.event/permission-granted
                                       :congregation/id cong-id
                                       :user/id user-id
                                       :permission/id :configure-congregation})
                  expected (-> expected
                               (assoc-in [::congregation/congregations cong-id :congregation/user-permissions user-id]
                                         #{:view-congregation
                                           :configure-congregation})
                               (assoc-in [::permissions/permissions user-id cong-id]
                                         {:view-congregation true
                                          :configure-congregation true}))]
              (is (= expected (apply-events events)))

              (testing "> another permission revoked"
                (let [events (conj events {:event/type :congregation.event/permission-revoked
                                           :congregation/id cong-id
                                           :user/id user-id
                                           :permission/id :configure-congregation})
                      expected (-> expected
                                   (assoc-in [::congregation/congregations cong-id :congregation/user-permissions user-id]
                                             #{:view-congregation})
                                   (assoc-in [::permissions/permissions user-id cong-id]
                                             {:view-congregation true}))]
                  (is (= expected (apply-events events)))))))))

      (testing "> congregation renamed"
        (let [events (conj events {:event/type :congregation.event/congregation-renamed
                                   :congregation/id cong-id
                                   :congregation/name "New Name"})
              expected (assoc-in expected [::congregation/congregations cong-id
                                           :congregation/name] "New Name")]
          (is (= expected (apply-events events)))))

      (testing "> settings updated"
        (let [events (conj events {:event/type :congregation.event/settings-updated
                                   :congregation/id cong-id
                                   :congregation/expire-shared-links-on-return false})
              expected (replace-in expected [::congregation/congregations cong-id
                                             :congregation/expire-shared-links-on-return] true false)]
          (is (= expected (apply-events events)))))

      (testing "> legacy settings updated event is ignored"
        (let [events (conj events {:event/type :congregation.event/settings-updated
                                   :congregation/id cong-id
                                   :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"})]
          (is (= expected (apply-events events))))))))

(deftest congregation-access-test
  (let [cong-id (UUID. 0 1)
        unrelated-cong-id (UUID. 0 2)
        user-id (UUID. 0 3)
        events [{:event/type :congregation.event/congregation-created
                 :congregation/id cong-id
                 :congregation/name "Cong1 Name"
                 :congregation/schema-name "cong1_schema"}
                {:event/type :congregation.event/congregation-created
                 :congregation/id unrelated-cong-id
                 :congregation/name "Cong2 Name"
                 :congregation/schema-name "cong2_schema"}]
        state (apply-events events)]

    (testing "cannot see congregations by default"
      (is (not (permissions/allowed? state user-id [:view-congregation cong-id])))
      (is (not (permissions/allowed? state user-id [:view-congregation unrelated-cong-id]))
          "unrelated congregation"))

    (let [events (conj events {:event/type :congregation.event/permission-granted
                               :congregation/id cong-id
                               :user/id user-id
                               :permission/id :view-congregation})
          state (apply-events events)]
      (testing "user was added to congregation"
        (is (= [user-id] (congregation/get-users state cong-id)))
        (is (empty? (congregation/get-users state unrelated-cong-id))
            "unrelated congregation"))

      (testing "can see congregation after granting access"
        (is (permissions/allowed? state user-id [:view-congregation cong-id]))
        (is (not (permissions/allowed? state user-id [:view-congregation unrelated-cong-id]))
            "unrelated congregation"))

      (let [events (conj events {:event/type :congregation.event/permission-revoked
                                 :congregation/id cong-id
                                 :user/id user-id
                                 :permission/id :view-congregation})
            state (apply-events events)]
        (testing "user was removed from congregation"
          (is (empty? (congregation/get-users state cong-id)))
          (is (empty? (congregation/get-users state unrelated-cong-id))
              "unrelated congregation"))

        (testing "cannot see congregation after revoking access"
          (is (not (permissions/allowed? state user-id [:view-congregation cong-id])))
          (is (not (permissions/allowed? state user-id [:view-congregation unrelated-cong-id]))
              "unrelated congregation"))))

    (testing "super user can view and configure all congregations"
      (let [state (congregation/sudo state user-id)]
        (is (= #{:view-congregation :configure-congregation}
               (permissions/list-permissions state user-id [cong-id])
               (permissions/list-permissions state user-id [unrelated-cong-id])))))))

(deftest check-congregation-exists-test
  (let [cong-id (UUID. 0 1)
        events [{:event/type :congregation.event/congregation-created
                 :congregation/id cong-id
                 :congregation/name "Cong1 Name"
                 :congregation/schema-name "cong1_schema"}]
        state (apply-events events)]

    (testing "exists"
      (is (nil? (congregation/check-congregation-exists state cong-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-congregation #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (congregation/check-congregation-exists state (UUID. 0 0x666)))))))


;;;; Commands

(deftest admin-permissions-granted-test
  (let [cong-id (UUID. 0 1)
        user-id (UUID. 0 2)
        events (congregation/admin-permissions-granted cong-id user-id)]
    (is (= {:event/type :congregation.event/permission-granted
            :congregation/id cong-id
            :user/id user-id
            :permission/id :view-congregation}
           (first events)))
    (is (= congregation/all-permissions
           (map :permission/id events)))))

(deftest create-congregation-test
  (let [cong-id (UUID. 0 1)
        user-id (UUID. 0 2)
        injections {:generate-tenant-schema-name (fn [id]
                                                   (is (= cong-id id))
                                                   "cong_schema")}
        create-command {:command/type :congregation.command/create-congregation
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :congregation/name "the name"}
        created-event {:event/type :congregation.event/congregation-created
                       :congregation/id cong-id
                       :congregation/name "the name"
                       :congregation/schema-name "cong_schema"}
        admin-grants (congregation/admin-permissions-granted cong-id user-id)]

    (testing "created"
      (is (= (concat [created-event] admin-grants)
             (handle-command create-command [] injections))))

    (testing "created by system, should make no grants"
      (let [command (-> create-command
                        (dissoc :command/user)
                        (assoc :command/system "test"))]
        (is (= [created-event]
               (handle-command command [] injections)))))

    (testing "error: name is blank"
      (let [command (assoc create-command :congregation/name "   ")]
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:missing-name]]")
             (handle-command command [] injections)))))

    (testing "create is idempotent"
      (is (empty? (handle-command create-command [created-event] injections))))))

(deftest rename-congregation-test
  (let [cong-id (UUID. 0 1)
        user-id (UUID. 0 2)
        injections {:check-permit (fn [_permit])}
        created-event {:event/type :congregation.event/congregation-created
                       :congregation/id cong-id
                       :congregation/name "old name"
                       :congregation/schema-name ""}
        rename-command {:command/type :congregation.command/update-congregation
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :congregation/name "new name"
                        :congregation/expire-shared-links-on-return true}
        renamed-event {:event/type :congregation.event/congregation-renamed
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
             ValidationException (re-equals "[[:missing-name]]")
             (handle-command command [created-event] injections)))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:configure-congregation cong-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command rename-command [created-event] injections)))))))

(deftest update-settings-test
  (let [cong-id (UUID. 0 1)
        user-id (UUID. 0 2)
        injections {:check-permit (fn [_permit])}
        created-event {:event/type :congregation.event/congregation-created
                       :congregation/id cong-id
                       :congregation/name "name"
                       :congregation/schema-name ""}
        base-command {:command/type :congregation.command/update-congregation
                      :command/time (Instant/now)
                      :command/user user-id
                      :congregation/id cong-id
                      :congregation/name "name"}
        base-event {:event/type :congregation.event/settings-updated
                    :congregation/id cong-id}]

    (testing "expire-shared-links-on-return"
      (let [enable-command (assoc base-command :congregation/expire-shared-links-on-return true)
            disable-command (assoc enable-command :congregation/expire-shared-links-on-return false)
            enabled-event (assoc base-event :congregation/expire-shared-links-on-return true)
            disabled-event (assoc enabled-event :congregation/expire-shared-links-on-return false)]

        (testing "enabled"
          (is (= [enabled-event]
                 (handle-command enable-command [created-event disabled-event] injections))))

        (testing "disabled"
          (is (= [disabled-event]
                 (handle-command disable-command [created-event enabled-event] injections))))

        (testing "not changed"
          (is (empty? (handle-command enable-command [created-event enabled-event] injections)))
          (is (empty? (handle-command disable-command [created-event disabled-event] injections))))))))

(deftest add-user-to-congregation-test
  (let [cong-id (UUID. 0 1)
        admin-id (UUID. 0 2)
        new-user-id (UUID. 0 3)
        injections {:check-permit (fn [_permit])}
        created-event {:event/type :congregation.event/congregation-created
                       :congregation/id cong-id
                       :congregation/name ""
                       :congregation/schema-name ""}
        add-user-command {:command/type :congregation.command/add-user
                          :command/time (Instant/now)
                          :command/user admin-id
                          :congregation/id cong-id
                          :user/id new-user-id}
        admin-grants (congregation/admin-permissions-granted cong-id new-user-id)
        view-granted {:event/type :congregation.event/permission-granted
                      :congregation/id cong-id
                      :user/id new-user-id
                      :permission/id :view-congregation}]

    (testing "user added"
      (is (= admin-grants
             (handle-command add-user-command [created-event] injections))))

    (testing "user already in congregation"
      (is (empty? (handle-command add-user-command [created-event view-granted] injections))))

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
        injections {:check-permit (fn [_permit])}
        created-event {:event/type :congregation.event/congregation-created
                       :congregation/id cong-id
                       :congregation/name ""
                       :congregation/schema-name ""}
        permission-granted-event {:event/type :congregation.event/permission-granted
                                  :congregation/id cong-id
                                  :user/id target-user-id
                                  :permission/id :PLACEHOLDER}
        permission-revoked-event {:event/type :congregation.event/permission-revoked
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

    (testing "cannot be used for adding new users to congregation"
      (let [given [created-event]
            when (assoc set-user-permissions-command :permission/ids [:view-congregation])]
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:user-not-in-congregation #uuid \"00000000-0000-0000-0000-000000000003\"]]")
             (handle-command when given injections)))))

    (testing "cannot revoke :view-congregation without also removing all other permissions"
      (let [given [created-event
                   (assoc permission-granted-event :permission/id :view-congregation)
                   (assoc permission-granted-event :permission/id :configure-congregation)]
            when (assoc set-user-permissions-command :permission/ids [:configure-congregation])]
        (is (thrown-with-msg?
             ValidationException (re-equals "[[:cannot-revoke-view-congregation]]")
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
