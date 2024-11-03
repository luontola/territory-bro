(ns ^:slow territory-bro.gis.gis-sync-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.gis.gis-db-test :refer [test-schema test-schema-fixture]]
            [territory-bro.gis.gis-sync :as gis-sync]
            [territory-bro.infra.db :as db]
            [territory-bro.test.fixtures :refer [db-fixture]])
  (:import (java.util.concurrent SynchronousQueue TimeUnit)))

(use-fixtures :each (join-fixtures [db-fixture test-schema-fixture]))

(deftest listen-for-gis-changes-test
  (let [notifications (SynchronousQueue.)
        worker (doto (Thread. ^Runnable (partial gis-sync/listen-for-gis-changes #(.put notifications "notified")))
                 (.setDaemon true)
                 (.start))]
    (try
      (testing "initial notification on startup"
        ;; This feature exists mainly to make these tests less fragile.
        ;; These tests used to be fail if the GIS changes were made before
        ;; the background thread had executed its LISTEN command.
        (is (some? (.poll notifications 1 TimeUnit/SECONDS))))

      (testing "notifies when there are GIS changes"
        (is (nil? (.poll notifications 1 TimeUnit/MILLISECONDS))
            "before change")
        (with-open [conn (db/get-tenant-connection test-schema)]
          (gis-db/create-region! conn "Somewhere" testdata/wkt-multi-polygon))
        (is (some? (.poll notifications 1 TimeUnit/SECONDS))
            "after change"))

      (testing "no notifications when there are no GIS changes"
        (is (nil? (.poll notifications 1 TimeUnit/MILLISECONDS))))

      (finally
        (.interrupt worker)))))
