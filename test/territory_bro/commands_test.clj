;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.commands-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [territory-bro.commands :as commands]
            [territory-bro.infra.foreign-key :as foreign-key]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.test.testutil :as testutil :refer [re-equals re-contains]])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException)
           (territory_bro.infra.foreign_key References)))

(def valid-command {:command/type :congregation.command/rename-congregation
                    :command/time (Instant/now)
                    :command/user (UUID/randomUUID)
                    :congregation/id (UUID/randomUUID)
                    :congregation/name ""})
(def invalid-command (dissoc valid-command :congregation/name))
(def unknown-command (assoc valid-command :command/type :foo))

(deftest sorted-keys-test
  (is (nil? (commands/sorted-keys nil)))
  (is (= [:command/type
          :command/user
          :command/time
          :congregation/id
          :congregation/name]
         (keys (commands/sorted-keys valid-command)))))

;; TODO: deduplicate event & command validation infra

(deftest command-schema-test
  (binding [foreign-key/*reference-checkers* testutil/dummy-reference-checkers]

    (testing "check specific command schema"
      (is (nil? (s/check commands/RenameCongregation valid-command))))

    (testing "check generic command schema"
      (is (nil? (s/check commands/Command valid-command))))

    (testing "invalid command"
      (is (= {:congregation/name 'missing-required-key}
             (s/check commands/Command invalid-command))))

    (testing "unknown command type"
      ;; TODO: produce a helpful error message
      (is (s/check commands/Command unknown-command)))

    (testing "all UUIDs are foreign-key checked"
      (letfn [(check-schema [schema path]
               ;; there should not be a schema where the value is a plain UUID
               ;; instead of a (foreign-key/references :stuff UUID)
                (is (not= UUID schema)
                    (str "missing a foreign key check at " path))
                (cond
                  ;; don't recurse into foreign-key/references
                  (instance? References schema) nil
                  ;; map? matches also records, which is most schema types
                  (map? schema) (doseq [[key val] schema]
                                  (check-schema key (conj path key))
                                  (check-schema val (conj path key)))
                  (vector? schema) (doseq [[idx val] (map-indexed vector schema)]
                                     (check-schema val (conj path idx)))))]
        (doseq [[type schema] commands/command-schemas]
          (testing {:command/type type}
            (check-schema schema [])))))))

(deftest validate-command-test
  (binding [foreign-key/*reference-checkers* testutil/dummy-reference-checkers]

    (testing "valid command"
      (is (= valid-command (testutil/validate-command valid-command))))

    (testing "invalid command"
      (is (thrown-with-msg? ExceptionInfo (re-contains "{:congregation/name missing-required-key}")
                            (testutil/validate-command invalid-command))))

    (testing "unknown command type"
      (is (thrown-with-msg? ExceptionInfo (re-equals "Unknown command type :foo")
                            (testutil/validate-command unknown-command))))))

(deftest validate-commands-test
  ;; this tests is here instead of the testutil namespace, because the command examples are here
  (is (= [] (testutil/validate-commands [])))
  (is (= [valid-command] (testutil/validate-commands [valid-command])))
  (is (thrown? ExceptionInfo (testutil/validate-commands [invalid-command]))))

(deftest check-permit-test
  (let [user-id (UUID. 0 1)
        state (permissions/grant {} user-id [:some-user-permit])]

    (testing "user has permission"
      (is (nil? (commands/check-permit state {:command/user user-id} [:some-user-permit]))))

    (testing "user doesn't have permission"
      (is (thrown? NoPermitException
                   (commands/check-permit state {:command/user user-id} [:arbitrary-permit]))))

    (testing "system always has permission"
      (is (nil? (commands/check-permit state {:command/system "sys"} [:some-user-permit])))
      (is (nil? (commands/check-permit state {:command/system "sys"} [:arbitrary-permit]))))

    (testing "system, on behalf of a user, always has permission"
      (is (nil? (commands/check-permit state {:command/system "sys", :command/user user-id} [:some-user-permit])))
      (is (nil? (commands/check-permit state {:command/system "sys", :command/user user-id} [:arbitrary-permit]))))

    (testing "error: user and system missing"
      (is (thrown-with-msg?
           IllegalArgumentException (re-equals ":command/user or :command/system required, but was: {:foo 123}")
           (commands/check-permit state {:foo 123} [:bar]))))))
