;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.gis-sync-test
  (:require [clojure.test :refer :all]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture]]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-db-test :refer [test-schema test-schema-fixture]]
            [territory-bro.gis-sync :as gis-sync]
            [territory-bro.testdata :as testdata])
  (:import (java.util.concurrent TimeUnit SynchronousQueue)))

(use-fixtures :each (join-fixtures [db-fixture test-schema-fixture]))

(deftest listen-for-gis-changes-test
  (let [notifications (SynchronousQueue.)
        worker (doto (Thread. ^Runnable (partial gis-sync/listen-for-gis-changes #(.put notifications "notified")))
                 (.setDaemon true)
                 (.start))]
    (try
      (testing "notifies when there are GIS changes"
        (db/with-db [conn {}]
          (db/use-tenant-schema conn test-schema)
          (gis-db/create-subregion! conn "Somewhere" testdata/wkt-multi-polygon))
        (is (some? (.poll notifications 1 TimeUnit/SECONDS))))

      (finally
        (.interrupt worker)))))
