;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin-test
  (:require [clojure.test :refer :all]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.testutil :as testutil])
  (:import (java.util UUID)))

(defn- apply-events [events]
  (testutil/apply-events db-admin/projection events))

(deftest projection-test
  (testing "congregation created"
    (let [cong-id (UUID. 0 1)
          user-id (UUID. 0 2)
          events [{:event/type :congregation.event/congregation-created
                   :event/version 1
                   :congregation/id cong-id
                   :congregation/name "Cong1 Name"
                   :congregation/schema-name "cong1_schema"}]
          expected {::db-admin/tenant-schemas-to-create #{{:congregation/schema-name "cong1_schema"}}}]
      (is (= expected (apply-events events))))))
