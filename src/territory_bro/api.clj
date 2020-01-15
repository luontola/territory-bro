;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.api
  (:require [camel-snake-kebab.core :as csk]
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
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.jwt :as jwt]
            [territory-bro.permissions :as permissions]
            [territory-bro.projections :as projections]
            [territory-bro.qgis :as qgis]
            [territory-bro.user :as user]
            [territory-bro.util :refer [getx]])
  (:import (com.auth0.jwt.exceptions JWTVerificationException)
           (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException)))

(def ^:private format-key-for-api (memoize (comp csk/->camelCaseString name)))

(defn format-for-api [m]
  (let [f (fn [x]
            (if (map? x)
              (map-keys format-key-for-api x)
              x))]
    (clojure.walk/postwalk f m)))

(defn require-logged-in! []
  (if-not auth/*user*
    (unauthorized! "Not logged in")))

(defn ^:dynamic save-user-from-jwt! [jwt]
  (db/with-db [conn {}]
    (user/save-user! conn (:sub jwt) (select-keys jwt auth/user-profile-keys))))

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

(defn- super-user? []
  (or (contains? (:super-users config/env) (:user/id auth/*user*))
      (contains? (:super-users config/env) (:sub auth/*user*))))

(defn sudo [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (if (super-user?)
      (let [session (-> (:session request)
                        (assoc ::sudo? true))]
        (log/info "Super user promotion")
        (-> (see-other "/")
            (assoc :session session)))
      (forbidden "Not super user"))))

(defn- fix-user-for-liberator [user]
  ;; TODO: custom serializer for UUID
  (if (some? (:user/id user))
    (update user :user/id str)
    user))

(defresource settings
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [request]}]
               (auth/with-authenticated-user request
                 (format-for-api
                  {:dev (getx config/env :dev)
                   :auth0 {:domain (getx config/env :auth0-domain)
                           :clientId (getx config/env :auth0-client-id)}
                   :supportEmail (when auth/*user*
                                   (getx config/env :support-email))
                   :user (when auth/*user*
                           (fix-user-for-liberator auth/*user*))}))))

(defn- current-user-id []
  (let [id (:user/id auth/*user*)]
    (assert id)
    id))

(defn- state-for-request [request]
  (let [state (projections/cached-state)]
    (if (::sudo? (:session request))
      (-> state
          (permissions/grant (current-user-id) [:view-congregation])
          (permissions/grant (current-user-id) [:configure-congregation]))
      state)))

(defn list-congregations [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [state (state-for-request request)]
      (ok (->> (congregation/get-my-congregations state (current-user-id))
               (map (fn [congregation]
                      {:id (:congregation/id congregation)
                       :name (:congregation/name congregation)})))))))

(defn get-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (db/with-db [conn {:read-only? true}]
      (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
            user-id (current-user-id)
            state (state-for-request request)
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
                             :territories (gis-db/get-territories conn)
                             :congregation-boundaries (gis-db/get-congregation-boundaries conn)
                             :subregions (gis-db/get-subregions conn)
                             :card-minimap-viewports (gis-db/get-card-minimap-viewports conn)
                             :users (->> (user/get-users conn {:ids (congregation/get-users state cong-id)})
                                         (map (fn [user]
                                                (-> (:user/attributes user)
                                                    (assoc :id (:user/id user))
                                                    (assoc :sub (:user/subject user))))))}))))))

(defn- api-command!
  ([conn state command]
   (api-command! conn state command {:message "OK"}))
  ([conn state command ok-response]
   ;; TODO: unit tests for this and other generic request mapping stuff
   (let [command (assoc command
                        :command/time ((:now config/env))
                        :command/user (current-user-id))]
     (try
       (dispatcher/command! conn state command)
       (ok ok-response)
       (catch ValidationException e
         (log/warn e "Invalid command:" command)
         (bad-request {:errors (.getErrors e)}))
       (catch NoPermitException e
         (log/warn e "Forbidden command:" command)
         (forbidden {:message "Forbidden"}))
       ;; TODO: catch WriteConflictException, return HTTP 409 CONFLICT
       (catch Throwable t
         ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
         (log/error t (str "Command failed: "
                           (pr-str command)
                           "\n"
                           (pr-str t)))
         (internal-server-error {:message "Internal Server Error"}))))))

(defn create-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [cong-id (UUID/randomUUID)
          name (get-in request [:params :name])
          state (state-for-request request)]
      (db/with-db [conn {}]
        (api-command! conn state {:command/type :congregation.command/create-congregation
                                  :congregation/id cong-id
                                  :congregation/name name}
                      {:id cong-id})))))

(defn add-user [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          user-id (UUID/fromString (get-in request [:params :userId]))
          state (state-for-request request)]
      (db/with-db [conn {}]
        (api-command! conn state {:command/type :congregation.command/add-user
                                  :congregation/id cong-id
                                  :user/id user-id})))))

(defn set-user-permissions [request]
  (auth/with-authenticated-user request
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          user-id (UUID/fromString (get-in request [:params :userId]))
          permissions (->> (get-in request [:params :permissions])
                           (map keyword))
          state (state-for-request request)]
      (db/with-db [conn {}]
        (api-command! conn state {:command/type :congregation.command/set-user-permissions
                                  :congregation/id cong-id
                                  :user/id user-id
                                  :permission/ids permissions})))))

(defn rename-congregation [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          name (get-in request [:params :name])
          state (state-for-request request)]
      (db/with-db [conn {}]
        (api-command! conn state {:command/type :congregation.command/rename-congregation
                                  :congregation/id cong-id
                                  :congregation/name name})))))

(defn download-qgis-project [request]
  (auth/with-authenticated-user request
    (require-logged-in!)
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          user-id (current-user-id)
          state (state-for-request request)
          congregation (congregation/get-my-congregation state cong-id user-id)
          gis-user (gis-user/get-gis-user state cong-id user-id)]
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
            (response/header "Content-Disposition" (str "attachment; filename=\"" file-name "\"")))))))

(defroutes api-routes
  (GET "/" [] (ok "Territory Bro"))
  (POST "/api/login" request (login request))
  (POST "/api/dev-login" request (dev-login request))
  (POST "/api/logout" [] (logout))
  (GET "/api/sudo" request (sudo request))
  (ANY "/api/settings" [] settings)
  (POST "/api/congregations" request (create-congregation request))
  (GET "/api/congregations" request (list-congregations request))
  (GET "/api/congregation/:congregation" request (get-congregation request))
  (POST "/api/congregation/:congregation/add-user" request (add-user request))
  (POST "/api/congregation/:congregation/set-user-permissions" request (set-user-permissions request))
  (POST "/api/congregation/:congregation/rename" request (rename-congregation request))
  (GET "/api/congregation/:congregation/qgis-project" request (download-qgis-project request)))

(comment
  (db/with-db [conn {:read-only? true}]
    (->> (user/get-users conn)
         (filter #(= "" (:name (:user/attributes %)))))))
