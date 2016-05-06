(ns territory-bro.routes.home
  (:require [territory-bro.db.core :as db]
            [territory-bro.domain :as domain]
            [territory-bro.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :refer [redirect]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import (java.time LocalDate)))

(defn overview-page []
  (layout/render "overview.html"
                 {:docs            (-> "docs/docs.md" io/resource slurp)
                  :territory-count (db/count-territories)
                  :region-count    (db/count-regions)}))

(defn territory-cards-page []
  (layout/render "territory-cards.html"
                 {:territories (db/find-territories)
                  :today       (LocalDate/now)}))

(defn neighborhood-maps-page []
  (layout/render "neighborhood-maps.html"
                 {:territories (db/find-territories)}))

(defn region-maps-page []
  (layout/render "region-maps.html"
                 {:regions          (db/find-regions)
                  :territories-json (json/write-str (db/find-territories))}))

(defn- read-file-upload [request param]
  (let [tempfile (-> request :params param :tempfile)]
    (try
      (slurp tempfile)
      (finally
        (io/delete-file tempfile)))))

(defn import-territories! [request]
  (let [territories-geojson (read-file-upload request :territories)]
    (when (not-empty territories-geojson)
      (db/transactional
        (db/delete-all-territories!)
        (dorun (map db/create-territory! (-> territories-geojson
                                             json/read-str
                                             domain/geojson-to-territories))))))
  (redirect "/"))

(defroutes home-routes
           (GET "/" [] (overview-page))
           (GET "/territory-cards" [] (territory-cards-page))
           (GET "/neighborhood-maps" [] (neighborhood-maps-page))
           (GET "/region-maps" [] (region-maps-page))
           (POST "/import-territories" request (import-territories! request)))
