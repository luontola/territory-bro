;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.routes
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
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
            [territory-bro.jwt :as jwt]
            [territory-bro.permissions :as perm]
            [territory-bro.util :refer [getx]])
  (:import (com.auth0.jwt.exceptions JWTVerificationException)
           (java.util UUID)))

(defn require-logged-in! []
  (if-not auth/*user*
    (http-res/unauthorized! "Not logged in")))

(defn find-tenant [request tenants]
  (if-let [tenant (get-in request [:headers "x-tenant"])]
    (let [tenant (keyword tenant)]
      (when (some #(= tenant %) tenants)
        tenant))))

(defn login [request]
  (let [id-token (get-in request [:params :idToken])
        jwt (try
              (jwt/validate id-token env)
              (catch JWTVerificationException e
                (log/info e "Login failed, invalid token")
                (http-res/forbidden! "Invalid token")))
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
                 (require-logged-in!)
                 (congregation/my-congregations))))

(defresource territories
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 (require-logged-in!)
                 (let [tenant (find-tenant request (perm/visible-congregations))]
                   (if-not tenant
                     (http-res/bad-request! (str "no such tenant: " tenant)))
                   (if-not (perm/can-view-territories? tenant)
                     (http-res/forbidden! (str "cannot view territories of " tenant)))
                   (db/as-tenant tenant
                     (db/query :find-territories))))))

(defresource regions
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 (require-logged-in!)
                 (let [tenant (find-tenant request (perm/visible-congregations))]
                   (if-not tenant
                     (http-res/bad-request! (str "no such tenant: " tenant)))
                   (if-not (perm/can-view-territories? tenant)
                     (http-res/forbidden! (str "cannot view regions of " tenant)))
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
    (require-logged-in!)
    (let [tenant (keyword (get-in request [:params :tenant]))
          ;; TODO: deduplicate with find-tenant
          tenant (when (some #(= tenant %) (perm/visible-congregations))
                   tenant)]
      (if-not (perm/can-modify-territories? tenant)
        (http-res/forbidden! (str "cannot modify territories of " tenant)))
      (let [content (generate-qgis-project (select-keys (get-in env [:tenant tenant])
                                                        [:database-host :database-username :database-password]))
            file-name (str (name tenant) "-territories.qgs")]
        (-> (http-res/ok content)
            (res/content-type "application/octet-stream")
            (res/header "Content-Disposition" (str "attachment; filename=\"" file-name "\"")))))))

(defn create-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [name (get-in request [:params :name])]
      (assert (not (str/blank? name))
              {:name name})
      (jdbc/with-db-transaction [conn (:default db/databases) {:isolation :serializable}]
        (jdbc/execute! conn [(str "set search_path to " (:database-schema env))]) ;; TODO: extract method
        (http-res/ok {:id (congregation/create-congregation! conn name)})))))

(defroutes api-routes
  (GET "/" [] (http-res/ok "Territory Bro"))
  (POST "/api/login" request (login request))
  (POST "/api/dev-login" request (dev-login request))
  (POST "/api/logout" [] (logout))
  (ANY "/api/settings" [] settings)
  (POST "/api/create-congregation" request (create-congregation request))
  (ANY "/api/my-congregations" [] my-congregations)
  (ANY "/api/territories" [] territories)
  (ANY "/api/regions" [] regions)
  (GET "/api/download-qgis-project/:tenant" request (download-qgis-project request)))
