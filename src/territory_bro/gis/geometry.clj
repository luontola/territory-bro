(ns territory-bro.gis.geometry
  (:require [clojure.tools.logging :as log])
  (:import (java.time ZoneId ZoneOffset)
           (net.iakovlev.timeshape TimeZoneEngine)
           (org.locationtech.jts.geom Coordinate Geometry GeometryFactory Polygon)
           (org.locationtech.jts.geom.util GeometryFixer)
           (org.locationtech.jts.io WKTReader)
           (org.locationtech.jts.util GeometricShapeFactory)))

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

(defn fit-tms-zoom-level [^Geometry geom]
  (let [envelope (.getEnvelopeInternal geom)
        geom-lon-deg (- (.getMaxX envelope) (.getMinX envelope))
        geom-lat-deg (- (.getMaxY envelope) (.getMinY envelope))

        ;; world size in degrees, in Mercator projection
        world-lon-deg 360.0
        world-lat-deg (* 2 85.05113) ; https://en.wikipedia.org/wiki/Mercator_projection#Truncation_and_aspect_ratio

        ;; zoom level where the tile is about the same size as the geom
        zoom-lon (/ (Math/log (/ world-lon-deg geom-lon-deg))
                    (Math/log 2))
        zoom-lat (/ (Math/log (/ world-lat-deg geom-lat-deg))
                    (Math/log 2))

        ;; take the minimum zoom level == zoom out to fit the whole geom
        zoom-level (int (Math/floor (min zoom-lon zoom-lat)))]
    (max 0 (min zoom-level 19))))

(defn enclosing-tms-tile [^Geometry geom]
  (let [centroid (-> geom .getCentroid .getCoordinate)
        lat (.getY centroid)
        lon (.getX centroid)
        zoom (-> (fit-tms-zoom-level geom)
                 (min 16)) ; if the territory is just one building, zoom further out to see the neighborhood

        n (Math/pow 2 zoom)
        x (int (* (/ (+ lon 180.0) 360.0) n))
        y (int (* (/ (- 1.0 (/ (Math/log (+ (Math/tan (Math/toRadians lat))
                                            (/ 1.0 (Math/cos (Math/toRadians lat)))))
                               Math/PI))
                     2.0)
                  n))]
    {:x x
     :y y
     :zoom zoom}))

(defn openstreetmap-tms-url [{:keys [x y zoom]}]
  (str "https://tile.openstreetmap.org/" zoom "/" x "/" y ".png"))

(defn union ^Geometry [geometries]
  (when-not (empty? geometries)
    (reduce Geometry/.union geometries)))


;;;; Helpers for tests

(defn circle ^Polygon [[center-x center-y] diameter num-points]
  (.createCircle
   (doto (GeometricShapeFactory. geometry-factory)
     (.setCentre (Coordinate. center-x center-y))
     (.setSize diameter)
     (.setNumPoints num-points))))

(defn rectangle ^Polygon [[start-x start-y] [end-x end-y]]
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
