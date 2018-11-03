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
            [territory-bro.authentication :as auth]
            [territory-bro.config :refer [env]]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.domain :as domain]))

(defn find-tenant [request env]
  (if-let [tenant (get-in request [:headers "x-tenant"])]
    (let [tenant (keyword tenant)]
      (when (contains? (:tenant env) tenant)
        tenant))))

(defn- read-file-upload [request param]
  (let [tempfile (-> request :params param :tempfile)]
    (try
      (slurp tempfile)
      (finally
        (io/delete-file tempfile)))))

(defn import-territories! [request]
  ; TODO: not needed for tenants, remove this method after the tenant system is complete
  (db/as-tenant nil
    (let [geojson (read-file-upload request :territories)]
      (when (not-empty geojson)
        (log/info "Importing territories")
        (db/transactional
         (db/query :delete-all-territories!)
         (dorun (map db/create-territory! (-> geojson
                                              json/read-str
                                              domain/geojson-to-territories))))))
    (let [geojson (read-file-upload request :regions)]
      (when (not-empty geojson)
        (log/info "Importing regions")
        (db/transactional
         (db/query :delete-all-regions!)
         (dorun (map db/create-region! (-> geojson
                                           json/read-str
                                           domain/geojson-to-regions)))))))
  (redirect "/"))

(defn clear-database! []
  ; TODO: not needed for tenants, remove this method after the tenant system is complete
  (db/as-tenant nil
    (log/info "Clearing the database")
    (db/transactional
     (db/query :delete-all-territories!)
     (db/query :delete-all-regions!)))
  (redirect "/"))

;; TODO: not really a resource, but a command
(defresource login
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :post! (fn [{:keys [request]}]
           (let [id-token (get-in request [:params :idToken])
                 jwt (auth/decode-jwt id-token)]
             ;; TODO: create session, save allowed tenants in session
             (prn 'jwt jwt))
           nil))

(defresource my-congregations
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (congregation/my-congregations)))

(defresource territories
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (db/as-tenant (find-tenant request env)
                 (db/query :find-territories))))

(defresource regions
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (db/as-tenant (find-tenant request env)
                 (db/query :find-regions))))

(defroutes home-routes
  (GET "/" [] {:status 200
               :headers {"Content-Type" "text/html; charset=utf-8"}
               :body "Territory Bro"})
  (ANY "/api/login" [] login)
  (ANY "/api/my-congregations" [] my-congregations)
  (ANY "/api/territories" [] territories)
  (ANY "/api/regions" [] regions)
  (POST "/api/import-territories" request (import-territories! request))
  (POST "/api/clear-database" [] (clear-database!)))
