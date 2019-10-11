;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.api
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
            [territory-bro.events :as events]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.jwt :as jwt]
            [territory-bro.permissions :as permissions]
            [territory-bro.projections :as projections]
            [territory-bro.qgis :as qgis]
            [territory-bro.region :as region]
            [territory-bro.territory :as territory]
            [territory-bro.user :as user]
            [territory-bro.util :refer [getx]])
  (:import (com.auth0.jwt.exceptions JWTVerificationException)
           (java.util UUID)
           (territory_bro NoPermitException)))

(defn require-logged-in! []
  (if-not auth/*user*
    (unauthorized! "Not logged in")))

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
        user-id (save-user-from-jwt! jwt)
        session (merge (:session request)
                       (auth/user-session jwt user-id))]
    (log/info "Logged in using JWT" jwt)
    (-> (ok "Logged in")
        (assoc :session session))))

(defn dev-login [request]
  (if (getx config/env :dev)
    (let [fake-jwt (:params request)
          user-id (save-user-from-jwt! fake-jwt)
          session (merge (:session request)
                         (auth/user-session fake-jwt user-id))]
      (log/info "Developer login as" fake-jwt)
      (-> (ok "Logged in")
          (assoc :session session)))
    (forbidden "Dev mode disabled")))

(defn logout []
  (log/info "Logged out")
  (-> (ok "Logged out")
      (assoc :session nil)))

(defn- fix-user-for-liberator [user]
  ;; TODO: custom serializer for UUID
  (if (some? (:user/id user))
    (update user :user/id str)
    user))

(defresource settings
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 {:dev (getx config/env :dev)
                  :auth0 {:domain (getx config/env :auth0-domain)
                          :clientId (getx config/env :auth0-client-id)}
                  :supportEmail (when auth/*user*
                                  (getx config/env :support-email))
                  :user (-> (select-keys auth/*user* [:user/id :name :sub])
                            (fix-user-for-liberator)
                            (assoc :authenticated (not (nil? auth/*user*))))})))

(defn- current-user-id [conn]
  (let [id (:user/id (user/get-by-subject conn (:sub auth/*user*)))]
    (assert id)
    id))

(defn create-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [name (get-in request [:params :name])]
      (assert (not (str/blank? name)) ; TODO: test this
              {:name name})
      (db/with-db [conn {}]
        (let [user-id (current-user-id conn)]
          (binding [events/*current-user* user-id]
            (let [cong-id (congregation/create-congregation! conn name)]
              (congregation/grant! conn cong-id user-id :view-congregation)
              (congregation/grant! conn cong-id user-id :configure-congregation)
              (gis-user/create-gis-user! conn (projections/current-state conn) cong-id user-id)
              (ok {:id cong-id}))))))))

(defn list-congregations [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (db/with-db [conn {:read-only? true}]
      (ok (->> (congregation/get-my-congregations (projections/cached-state) (current-user-id conn))
               (map (fn [congregation]
                      {:id (:congregation/id congregation)
                       :name (:congregation/name congregation)})))))))

(def ^:private format-key-for-api (memoize (comp csk/->camelCaseString name)))

(defn format-for-api [m]
  (let [f (fn [x]
            (if (map? x)
              (map-keys format-key-for-api x)
              x))]
    (clojure.walk/postwalk f m)))

(defn get-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (db/with-db [conn {:read-only? true}]
      (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
            user-id (current-user-id conn)
            state (projections/cached-state)
            congregation (congregation/get-my-congregation state cong-id user-id)]
        (when-not congregation
          (forbidden! "No congregation access"))
        (db/use-tenant-schema conn (:congregation/schema-name congregation))
        (ok (format-for-api {:id (:congregation/id congregation)
                             :name (:congregation/name congregation)
                             :permissions (->> (permissions/list-permissions state user-id [cong-id])
                                               (map (fn [permission]
                                                      [permission true]))
                                               (into {}))
                             :territories (territory/get-territories conn)
                             :congregation-boundaries (region/get-congregation-boundaries conn)
                             :subregions (region/get-subregions conn)
                             :card-minimap-viewports (region/get-card-minimap-viewports conn)}))))))

(defn- api-command! [conn command]
  (let [command (assoc command
                       :command/time ((:now config/env))
                       :command/user (current-user-id conn))]
    (try
      (congregation/command! conn (projections/cached-state)
                             command)
      (ok {:message "OK"})
      (catch NoPermitException e
        (log/warn e "Command denied:" command)
        (forbidden {:message "Forbidden"})))))

(defn add-user [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          user-id (UUID/fromString (get-in request [:params :userId]))]
      (db/with-db [conn {}]
        (api-command! conn {:command/type :congregation.command/add-user
                            :congregation/id cong-id
                            :user/id user-id})))))

(defn rename-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          name (get-in request [:params :name])]
      (db/with-db [conn {}]
        (api-command! conn {:command/type :congregation.command/rename-congregation
                            :congregation/id cong-id
                            :congregation/name name})))))

(defn download-qgis-project [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (db/with-db [conn {:read-only? true}]
      (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
            user-id (current-user-id conn)
            congregation (congregation/get-my-congregation (projections/cached-state) cong-id user-id)
            gis-user (gis-user/get-gis-user (projections/cached-state) cong-id user-id)]
        (when-not gis-user
          (forbidden! "No GIS access"))
        (let [content (qgis/generate-project {:database-host (:gis-database-host config/env)
                                              :database-name (:gis-database-name config/env)
                                              :database-schema (:congregation/schema-name congregation)
                                              :database-username (:gis-user/username gis-user)
                                              :database-password (:gis-user/password gis-user)
                                              :database-ssl-mode (:gis-database-ssl-mode config/env)})
              file-name (qgis/project-file-name (:congregation/name congregation))]
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
  (POST "/api/congregation/:congregation/add-user" request (add-user request))
  (POST "/api/congregation/:congregation/rename" request (rename-congregation request))
  (GET "/api/congregation/:congregation/qgis-project" request (download-qgis-project request)))

(comment
  (db/with-db [conn {:read-only? true}]
    (->> (user/get-users conn)
         (filter #(= "" (:name (:user/attributes %))))))

  (db/with-db [conn {}]
    (let [user-id (UUID/fromString "")
          cong-id (UUID/fromString "")]
      (binding [events/*current-system* "admin"]
        (congregation/grant! conn cong-id user-id :view-congregation)
        (congregation/grant! conn cong-id user-id :configure-congregation)
        (gis-user/create-gis-user! conn (projections/current-state conn) cong-id user-id))))

  (db/with-db [conn {}]
    (binding [events/*current-system* "admin"]
      (doseq [{:keys [cong-id user-id perms]}
              (->> (::congregation/congregations (projections/cached-state))
                   (mapcat (fn [[cong-id cong]]
                             (map (fn [[user-id perms]]
                                    {:cong-id cong-id
                                     :user-id user-id
                                     :perms perms})
                                  (:congregation/user-permissions cong)))))]
        (when (and (:view-congregation perms)
                   (not (:configure-congregation perms)))
          (congregation/grant! conn cong-id user-id :configure-congregation))))))
