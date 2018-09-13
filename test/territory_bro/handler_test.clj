; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [territory-bro.db :as db]
            [territory-bro.db.migrations :as migrations]
            [territory-bro.handler :refer :all]))

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
