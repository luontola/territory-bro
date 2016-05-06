(ns territory-bro.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [territory-bro.handler :refer :all]
            [territory-bro.db.migrations :as migrations]
            [territory-bro.db.core :as db]))

(use-fixtures
  :once
  (fn [f]
    (db/connect!)
    (migrations/migrate ["migrate"])
    (f)))

(deftest test-app
  (testing "main route"
    (let [response (app (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response (app (request :get "/invalid"))]
      (is (= 404 (:status response))))))
