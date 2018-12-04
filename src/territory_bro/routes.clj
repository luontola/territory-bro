;; Copyright © 2015-2018 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.routes
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes GET POST ANY]]
            [liberator.core :refer [defresource]]
            [ring.util.http-response :as http-res]
            [ring.util.response :as res]
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
  (res/redirect "/"))

(defn clear-database! []
  ; TODO: not needed for tenants, remove this method after the tenant system is complete
  (db/as-tenant nil
    (log/info "Clearing the database")
    (db/transactional
      (db/query :delete-all-territories!)
      (db/query :delete-all-regions!)))
  (res/redirect "/"))

(defn login [request]
  (let [id-token (get-in request [:params :idToken])
        jwt (jwt/validate id-token env)
        session (merge (:session request)
                       (auth/user-session jwt env))]
    (log/info "Logged in using JWT" jwt)
    (-> (http-res/ok "Logged in")
        (assoc :session session))))

(defn dev-login [request]
  (if (getx env :dev)
    (let [fake-jwt (:params request)
          session (merge (:session request)
                         (auth/user-session fake-jwt env))]
      (log/info "Developer login as" fake-jwt)
      (-> (http-res/ok "Logged in")
          (assoc :session session)))
    (http-res/not-found "Dev mode disabled")))

(defn logout []
  (log/info "Logged out")
  (-> (http-res/ok "Logged out")
      (assoc :session nil)))

(defresource settings
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 {:dev (getx env :dev)
                  :auth0 {:domain (getx env :auth0-domain)
                          :clientId (getx env :auth0-client-id)}
                  :supportEmail (getx env :support-email)
                  :user (assoc (select-keys auth/*user* [:name :sub])
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

(def qgis-project-template (io/resource "template-territories.qgs"))

(defn generate-qgis-project [{:keys [database-host database-username database-password]}]
  (assert database-host "host is missing")
  (assert database-username "username is missing")
  (assert database-password "password is missing")
  (-> (slurp qgis-project-template :encoding "UTF-8")
      (str/replace "HOST_GOES_HERE" database-host)
      (str/replace "USERNAME_GOES_HERE" database-username)
      (str/replace "PASSWORD_GOES_HERE" database-password)))

(defn download-qgis-project [request]
  (auth/with-authenticated-user request
    (let [tenant (keyword (get-in request [:params :tenant]))
          ;; TODO: deduplicate with find-tenant
          tenant (when (some #(= tenant %) (perm/visible-congregations))
                   tenant)]
      (assert (and tenant
                   (perm/can-modify-territories? tenant))
              (str "cannot modify territories of " tenant))
      (let [content (generate-qgis-project (select-keys (get-in env [:tenant tenant])
                                                        [:database-host :database-username :database-password]))
            file-name (str (name tenant) "-territories.qgs")]
        (-> (http-res/ok content)
            (res/content-type "application/octet-stream")
            (res/header "Content-Disposition" (str "attachment; filename=\"" file-name "\"")))))))

(defroutes api-routes
  (GET "/" [] (http-res/ok "Territory Bro"))
  (POST "/api/login" request (login request))
  (POST "/api/dev-login" request (dev-login request))
  (POST "/api/logout" [] (logout))
  (ANY "/api/settings" [] settings)
  (ANY "/api/my-congregations" [] my-congregations)
  (ANY "/api/territories" [] territories)
  (ANY "/api/regions" [] regions)
  (GET "/api/download-qgis-project/:tenant" request (download-qgis-project request))
  (POST "/api/import-territories" request (import-territories! request))
  (POST "/api/clear-database" [] (clear-database!)))
