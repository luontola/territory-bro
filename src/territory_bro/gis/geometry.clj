;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.geometry
  (:import (java.time ZoneId ZoneOffset)
           (net.iakovlev.timeshape TimeZoneEngine)
           (org.locationtech.jts.io WKTReader)))

(defonce ^:private *timezone-engine (future (TimeZoneEngine/initialize))) ; takes 370ms on an Apple M3 Max, so worth initializing on the background

(defn- timezone-engine ^TimeZoneEngine []
  @*timezone-engine)

(defn timezone-for-location ^ZoneId [^String location]
  (try
    (let [point (-> (WKTReader.)
                    (.read location)
                    (.getInteriorPoint))]
      (-> (.query (timezone-engine) (.getY point) (.getX point))
          (.orElse ZoneOffset/UTC)))
    (catch Exception _
      ZoneOffset/UTC)))
