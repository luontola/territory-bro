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
            [ring.util.response :refer [redirect response]]
            [territory-bro.authentication :as auth]
            [territory-bro.config :refer [env]]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.domain :as domain]
            [territory-bro.jwt :as jwt]
            [territory-bro.permissions :as perm]
            [territory-bro.util :refer [getx]]))

(defn find-tenant [request tenants]
  (if-let [tenant (get-in request [:headers "x-tenant"])]
    (let [tenant (keyword tenant)]
      (when (some #(= tenant %) tenants)
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

(defn login [request]
  (let [id-token (get-in request [:params :idToken])
        jwt (jwt/validate id-token env)
        session (merge (:session request)
                       (auth/user-session jwt env))]
    (log/info "Logged in using JWT" jwt)
    (-> (response "Logged in")
        (assoc :session session))))

(defn logout []
  (log/info "Logged out")
  (-> (response "Logged out")
      (assoc :session nil)))

(defresource settings
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 {:auth0 {:domain (getx env :auth0-domain)
                          :clientId (getx env :auth0-client-id)}
                  :user (assoc (select-keys auth/*user* [:name])
                          :authenticated (not (nil? auth/*user*)))
                  :congregations (congregation/my-congregations)})))

(defresource my-congregations
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 (congregation/my-congregations))))

(defresource territories
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 (let [tenant (find-tenant request (perm/visible-congregations))]
                   (assert (or (nil? tenant) ;; TODO: remove default tenant support
                               (perm/can-view-territories? tenant))
                           (str "cannot view territories of " tenant))
                   (db/as-tenant tenant
                     (db/query :find-territories))))))

(defresource regions
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 (let [tenant (find-tenant request (perm/visible-congregations))]
                   (assert (or (nil? tenant) ;; TODO: remove default tenant support
                               (perm/can-view-territories? tenant))
                           (str "cannot view regions of " tenant))
                   (db/as-tenant tenant
                     (db/query :find-regions))))))

(defroutes home-routes
  (GET "/" [] (response "Territory Bro"))
  (POST "/api/login" request (login request))
  (POST "/api/logout" [] (logout))
  (ANY "/api/settings" [] settings)
  (ANY "/api/my-congregations" [] my-congregations)
  (ANY "/api/territories" [] territories)
  (ANY "/api/regions" [] regions)
  (POST "/api/import-territories" request (import-territories! request))
  (POST "/api/clear-database" [] (clear-database!)))
