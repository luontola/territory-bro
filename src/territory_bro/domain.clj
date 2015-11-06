(ns territory-bro.domain
  (:require [territory-bro.db.core :as db]))

(defn import-territories-geojson! [data]
  (assert (= "FeatureCollection" (get data "type"))
          (str "Not a FeatureCollection: " data))
  (db/create-territory! {:name    "foo2"
                         :address "fdas"
                         :area    nil}))
