(ns territory-bro.ui-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.ui :as ui])
  (:import (clojure.lang ExceptionInfo)
           (java.util UUID)))

(def not-found
  {:type :ring.util.http-response/response
   :response {:status 404
              :body "Not found"
              :headers {}}})

(deftest wrap-parse-path-params-test
  (let [handle (ui/wrap-parse-path-params identity)]
    (testing "no path params - no changes"
      (is (= {} (handle {})))
      (is (= {:path-params {}} (handle {:path-params {}}))))

    (testing "unrecognized path params - no changes"
      (is (= {:path-params {:foo "bar"}}
             (handle {:path-params {:foo "bar"}}))))

    (testing "parse UUID"
      (is (= {:path-params {:congregation (UUID. 0 1)}}
             (handle {:path-params {:congregation "00000000-0000-0000-0000-000000000001"}})))
      (is (= {:path-params {:congregation (UUID. 0 1)
                            :territory (UUID. 0 2)}}
             (handle {:path-params {:congregation "00000000-0000-0000-0000-000000000001"
                                    :territory "00000000-0000-0000-0000-000000000002"}})))
      (is (= {:path-params {:congregation (UUID. 0 1)
                            :publisher (UUID. 0 3)}}
             (handle {:path-params {:congregation "00000000-0000-0000-0000-000000000001"
                                    :publisher "00000000-0000-0000-0000-000000000003"}})))
      (is (= {:path-params {:congregation (UUID. 0 1)
                            :territory (UUID. 0 2)
                            :assignment (UUID. 0 4)}}
             (handle {:path-params {:congregation "00000000-0000-0000-0000-000000000001"
                                    :territory "00000000-0000-0000-0000-000000000002"
                                    :assignment "00000000-0000-0000-0000-000000000004"}}))))

    (testing "parse 'demo' congregation"
      (is (= {:path-params {:congregation "demo"}}
             (handle {:path-params {:congregation "demo"}}))))

    (testing "'demo' works only for the congregation"
      (is (thrown-match? ExceptionInfo not-found
                         (handle {:path-params {:territory "demo"}}))))

    (testing "disallow non-UUID parameters"
      (is (thrown-match? ExceptionInfo not-found
                         (handle {:path-params {:congregation "foo"}})))
      (is (thrown-match? ExceptionInfo not-found
                         (handle {:path-params {:territory "foo"}}))))))
