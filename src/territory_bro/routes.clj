(ns territory-bro.routes
  (:require [territory-bro.db.core :as db]
            [territory-bro.domain :as domain]
            [territory-bro.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :refer [redirect]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import (java.time LocalDate)))

(defn overview-page [request]
  (layout/render "overview.html"
                 request
                 {:docs            (-> "docs/docs.md" io/resource slurp)
                  :territory-count (db/count-territories)
                  :region-count    (db/count-regions)}))

(defn territory-cards-page [request]
  (layout/render "territory-cards.html"
                 request
                 {:territories (db/find-territories)
                  :today       (LocalDate/now)}))

(defn neighborhood-maps-page [request]
  (layout/render "neighborhood-maps.html"
                 request
                 {:territories (db/find-territories)}))

(defn region-maps-page [request]
  (layout/render "region-maps.html"
                 request
                 {:regions          (db/find-regions)
                  :territories-json (json/write-str (db/find-territories))}))

(defn- read-file-upload [request param]
  (let [tempfile (-> request :params param :tempfile)]
    (try
      (slurp tempfile)
      (finally
        (io/delete-file tempfile)))))

(defn import-territories! [request]
  (let [geojson (read-file-upload request :territories)]
    (when (not-empty geojson)
      (db/transactional
        (db/delete-all-territories!)
        (dorun (map db/create-territory! (-> geojson
                                             json/read-str
                                             domain/geojson-to-territories))))))
  (let [geojson (read-file-upload request :regions)]
    (when (not-empty geojson)
      (db/transactional
        (db/delete-all-regions!)
        (dorun (map db/create-region! (-> geojson
                                          json/read-str
                                          domain/geojson-to-regions))))))
  (redirect "/"))

(defn clear-database! []
  (db/transactional
    (db/delete-all-territories!)
    (db/delete-all-regions!))
  (redirect "/"))

(defroutes home-routes
           (GET "/" request (overview-page request))
           (GET "/territory-cards" request (territory-cards-page request))
           (GET "/neighborhood-maps" request (neighborhood-maps-page request))
           (GET "/region-maps" request (region-maps-page request))
           (POST "/import-territories" request (import-territories! request))
           (POST "/clear-database" [] (clear-database!)))
