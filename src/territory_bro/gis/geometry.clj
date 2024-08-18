;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.geometry
  (:require [clojure.tools.logging :as log])
  (:import (java.time ZoneId ZoneOffset)
           (net.iakovlev.timeshape TimeZoneEngine)
           (org.locationtech.jts.geom Coordinate Geometry GeometryFactory Polygon)
           (org.locationtech.jts.geom.util GeometryFixer)
           (org.locationtech.jts.io WKTReader)))

(def ^GeometryFactory geometry-factory (GeometryFactory.))

(defonce ^:private *timezone-engine (future (TimeZoneEngine/initialize))) ; takes 370ms on an Apple M3 Max, so worth initializing on the background
(defn timezone-engine ^TimeZoneEngine []
  @*timezone-engine)

(defn parse-wkt ^Geometry [^String wkt]
  (when (some? wkt)
    (try
      (let [geom (-> (WKTReader. geometry-factory)
                     (.read wkt))]
        (if (.isValid geom)
          geom
          (GeometryFixer/fix geom)))
      (catch Exception e
        (log/warn e "Failed to parse WKT" (pr-str wkt))
        nil))))

(defn timezone ^ZoneId [^Geometry location]
  (try
    (let [point (.getInteriorPoint location)]
      (-> (.query (timezone-engine) (.getY point) (.getX point))
          (.orElse ZoneOffset/UTC)))
    (catch Exception _
      ZoneOffset/UTC)))

(defn find-enclosing ^Geometry [^Geometry needle haystack]
  (->> haystack
       (filter #(.intersects needle ^Geometry %))
       ;; if there are more than one match
       (sort-by (fn [^Geometry enclosing]
                  [(- (.getArea (.intersection needle enclosing))) ; firstly prefer the area which intersects the most
                   (.getArea enclosing)])) ; secondly prefer the smallest enclosing area
       (first)))

(defn union ^Geometry [geometries]
  (when-not (empty? geometries)
    (reduce Geometry/.union geometries)))


;;;; Helpers for tests

(defn square ^Polygon [[start-x start-y] [end-x end-y]]
  (.createPolygon geometry-factory
                  ^Coordinate/1
                  (into-array Coordinate [(Coordinate. start-x start-y)
                                          (Coordinate. end-x start-y)
                                          (Coordinate. end-x end-y)
                                          (Coordinate. start-x end-y)
                                          (Coordinate. start-x start-y)])))

(defn equals? [^Geometry a ^Geometry b]
  (and (.covers a b)
       (.covers b a)))
