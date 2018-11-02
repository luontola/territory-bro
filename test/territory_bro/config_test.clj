; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.config-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [territory-bro.config :refer :all]
            [territory-bro.testing :refer [re-equals]]))

(deftest override-defaults-test
  (testing "default values"
    (is (= {:a "original"} (override-defaults {:a "original"} {}))))

  (testing "overridden default values"
    (is (= {:a "override"} (override-defaults {:a "original"} {:a "override"}))))

  (testing "ignores overrides which are not in defaults"
    (is (= {:a 1} (override-defaults {:a 1} {:b 2}))))

  (testing "nested levels may have overrides which are not in defaults"
    (is (= {:prefix {:a 1, :b 2}} (override-defaults {:prefix nil} {:prefix {:a 1, :b 2}}))))

  (testing "multiple overrides are all applied in order"
    (is (= {:a 2, :b 3} (override-defaults {:a 1, :b 1} {:a 2, :b 2} {:b 3})))))

(deftest database-url-test
  (let [env {:database-url "default url"
             :tenant {:foo {:database-url "foo url"}
                      :bar {:database-url "bar url"}}}]
    (testing "default database"
      (is (= "default url" (database-url env))))

    (testing "tenant database"
      (is (= "foo url" (database-url env :foo)))
      (is (= "bar url" (database-url env :bar))))

    (testing "unknown tenant is an error"
      (is (thrown-with-msg? IllegalArgumentException (re-equals "tenant not found: :no-such-tenant")
                            (database-url env :no-such-tenant))))))
