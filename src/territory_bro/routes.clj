; Copyright © 2015-2017 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.routes
  (:require [territory-bro.db.core :as db]
            [territory-bro.domain :as domain]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.api.sweet :as api]
            [ring.util.response :refer [redirect]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import (java.time LocalDate)))

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
           (GET "/" request {:status  200
                             :headers {"Content-Type" "text/html; charset=utf-8"}
                             :body    "Territory Bro"})
           (POST "/api/import-territories" request (import-territories! request))
           (POST "/api/clear-database" [] (clear-database!)))

(api/defapi api-routes
  (api/GET "/api/territories" []
    (ok (db/find-territories)))
  (api/GET "/api/regions" []
    (ok (db/find-regions))))
