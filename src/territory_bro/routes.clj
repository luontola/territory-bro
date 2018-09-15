; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.routes
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes GET POST ANY]]
            [liberator.core :refer [defresource]]
            [ring.util.http-response :refer [ok]]
            [ring.util.response :refer [redirect]]
            [territory-bro.db :as db]
            [territory-bro.domain :as domain]))

(defn- read-file-upload [request param]
  (let [tempfile (-> request :params param :tempfile)]
    (try
      (slurp tempfile)
      (finally
        (io/delete-file tempfile)))))

(defn import-territories! [request]
  (let [geojson (read-file-upload request :territories)]
    (when (not-empty geojson)
      (log/info "Importing territories")
      (db/transactional
       (db/delete-all-territories!)
       (dorun (map db/create-territory! (-> geojson
                                            json/read-str
                                            domain/geojson-to-territories))))))
  (let [geojson (read-file-upload request :regions)]
    (when (not-empty geojson)
      (log/info "Importing regions")
      (db/transactional
       (db/delete-all-regions!)
       (dorun (map db/create-region! (-> geojson
                                         json/read-str
                                         domain/geojson-to-regions))))))
  (redirect "/"))

(defn clear-database! []
  (log/info "Clearing the database")
  (db/transactional
   (db/delete-all-territories!)
   (db/delete-all-regions!))
  (redirect "/"))

(defresource territories
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (db/find-territories)))

(defresource regions
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (db/find-regions)))

(defroutes home-routes
  (GET "/" request {:status 200
                    :headers {"Content-Type" "text/html; charset=utf-8"}
                    :body "Territory Bro"})
  (ANY "/api/territories" [] territories)
  (ANY "/api/regions" [] regions)
  (POST "/api/import-territories" request (import-territories! request))
  (POST "/api/clear-database" [] (clear-database!)))
