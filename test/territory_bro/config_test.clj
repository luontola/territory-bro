;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.config-test
  (:require [clojure.test :refer :all]
            [territory-bro.config :refer :all]
            [territory-bro.testutil :refer [re-equals]]
            [territory-bro.util :refer [getx]])
  (:import (java.time Instant)))

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

(deftest enrich-env-test
  (let [env (enrich-env {:auth0-domain "example.eu.auth0.com"
                         :auth0-client-id "m14ziOMuEVgHB4LIzoLKeDXazSReXCZo"})]
    (testing ":now is a function which returns current time"
      (let [result ((getx env :now))]
        (is (instance? Instant result))
        (is (< (- (System/currentTimeMillis)
                  (.toEpochMilli ^Instant result))
               100))))

    (testing ":jwt-issuer is https://YOUR_AUTH0_DOMAIN/"
      (is (= "https://example.eu.auth0.com/"
             (getx env :jwt-issuer))))

    (testing ":jwt-audience is the Auth0 Client ID"
      (is (= "m14ziOMuEVgHB4LIzoLKeDXazSReXCZo"
             (getx env :jwt-audience))))))
