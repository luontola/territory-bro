;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.routes
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes GET POST ANY]]
            [liberator.core :refer [defresource]]
            [medley.core :refer [map-keys]]
            [ring.util.http-response :refer :all]
            [ring.util.response :as response]
            [territory-bro.authentication :as auth]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.jwt :as jwt]
            [territory-bro.qgis :as qgis]
            [territory-bro.region :as region]
            [territory-bro.territory :as territory]
            [territory-bro.user :as user]
            [territory-bro.util :refer [getx]])
  (:import (com.auth0.jwt.exceptions JWTVerificationException)
           (java.util UUID)))

(defn require-logged-in! []
  (if-not auth/*user*
    (unauthorized! "Not logged in")))

(defn find-tenant [request tenants]
  (if-let [tenant (get-in request [:headers "x-tenant"])]
    (let [tenant (keyword tenant)]
      (when (some #(= tenant %) tenants)
        tenant))))

(defn ^:dynamic save-user-from-jwt! [jwt]
  (db/with-db [conn {}]
    (user/save-user! conn (:sub jwt) (select-keys jwt [:name :nickname :email :email_verified :picture]))))

(defn login [request]
  (let [id-token (get-in request [:params :idToken])
        jwt (try
              (jwt/validate id-token config/env)
              (catch JWTVerificationException e
                (log/info e "Login failed, invalid token")
                (forbidden! "Invalid token")))
        session (merge (:session request)
                       (auth/user-session jwt))]
    (log/info "Logged in using JWT" jwt)
    (save-user-from-jwt! jwt)
    (-> (ok "Logged in")
        (assoc :session session))))

(defn dev-login [request]
  (if (getx config/env :dev)
    (let [fake-jwt (:params request)
          session (merge (:session request)
                         (auth/user-session fake-jwt))]
      (log/info "Developer login as" fake-jwt)
      (save-user-from-jwt! fake-jwt)
      (-> (ok "Logged in")
          (assoc :session session)))
    (forbidden "Dev mode disabled")))

(defn logout []
  (log/info "Logged out")
  (-> (ok "Logged out")
      (assoc :session nil)))

(defresource settings
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 {:dev (getx config/env :dev)
                  :auth0 {:domain (getx config/env :auth0-domain)
                          :clientId (getx config/env :auth0-client-id)}
                  :supportEmail (getx config/env :support-email)
                  :user (assoc (select-keys auth/*user* [:name :sub])
                               :authenticated (not (nil? auth/*user*)))})))

(defn- current-user-id [conn]
  (::user/id (user/get-by-subject conn (:sub auth/*user*))))

(defn create-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [name (get-in request [:params :name])]
      (assert (not (str/blank? name)) ; TODO: test this
              {:name name})
      (db/with-db [conn {}]
        (let [cong-id (congregation/create-congregation! conn name)
              user-id (current-user-id conn)]
          (congregation/grant-access! conn cong-id user-id)
          (gis-user/create-gis-user! conn cong-id user-id)
          (ok {:id cong-id}))))))

(defn list-congregations [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (db/with-db [conn {}]
      (ok (->> (congregation/get-my-congregations conn (current-user-id conn))
               (map (fn [congregation]
                      {:id (::congregation/id congregation)
                       :name (::congregation/name congregation)})))))))

(defn format-for-api [m]
  (let [convert-keyword (comp csk/->camelCaseString name)
        f (fn [x]
            (if (map? x)
              (map-keys convert-keyword x)
              x))]
    (clojure.walk/postwalk f m)))

(defn get-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (db/with-db [conn {}]
      (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
            congregation (congregation/get-my-congregation conn cong-id (current-user-id conn))]
        (when-not congregation
          (forbidden! "No congregation access"))
        (db/use-tenant-schema conn (::congregation/schema-name congregation))
        (ok (format-for-api {:id (::congregation/id congregation)
                             :name (::congregation/name congregation)
                             :territories (territory/get-territories conn)
                             :congregation-boundaries (region/get-congregation-boundaries conn)
                             :subregions (region/get-subregions conn)
                             :card-minimap-viewports (region/get-card-minimap-viewports conn)}))))))

(defn download-qgis-project [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (db/with-db [conn {}]
      (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
            user-id (current-user-id conn)
            congregation (congregation/get-my-congregation conn cong-id user-id)
            gis-user (gis-user/get-gis-user conn cong-id user-id)]
        (when-not gis-user
          (forbidden! "No GIS access"))
        (let [content (qgis/generate-project {:database-host (:gis-database-host config/env)
                                              :database-name (:gis-database-name config/env)
                                              :database-schema (::congregation/schema-name congregation)
                                              :database-username (::gis-user/username gis-user)
                                              :database-password ((::gis-user/password gis-user))})
              file-name (qgis/project-file-name (::congregation/name congregation))]
          (-> (ok content)
              (response/content-type "application/octet-stream")
              (response/header "Content-Disposition" (str "attachment; filename=\"" file-name "\""))))))))

(defroutes api-routes
  (GET "/" [] (ok "Territory Bro"))
  (POST "/api/login" request (login request))
  (POST "/api/dev-login" request (dev-login request))
  (POST "/api/logout" [] (logout))
  (ANY "/api/settings" [] settings)
  (POST "/api/congregations" request (create-congregation request))
  (GET "/api/congregations" request (list-congregations request))
  (GET "/api/congregation/:congregation" request (get-congregation request))
  (GET "/api/congregation/:congregation/qgis-project" request (download-qgis-project request)))
