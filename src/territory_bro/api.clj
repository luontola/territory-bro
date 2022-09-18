;; Copyright © 2015-2022 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.api
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [ANY GET POST defroutes]]
            [liberator.core :refer [defresource]]
            [medley.core :refer [map-keys]]
            [ring.util.http-response :refer :all]
            [ring.util.response :as response]
            [schema.core :as s]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.facade :as facade]
            [territory-bro.domain.loan :as loan]
            [territory-bro.domain.share :as share]
            [territory-bro.gis.gis-user :as gis-user]
            [territory-bro.gis.qgis :as qgis]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.jwt :as jwt]
            [territory-bro.infra.user :as user]
            [territory-bro.infra.util :refer [conj-set getx]]
            [territory-bro.projections :as projections])
  (:import (com.auth0.jwt.exceptions JWTVerificationException)
           (java.time Duration)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException WriteConflictException)))

(s/defschema Territory
  {:id s/Uuid
   :number s/Str
   :addresses s/Str
   :region s/Str
   :meta {s/Keyword s/Any}
   :location s/Str
   (s/optional-key :loaned) s/Bool
   (s/optional-key :staleness) s/Int})

(s/defschema Region
  {:id s/Uuid
   :name s/Str
   :location s/Str})

(s/defschema CongregationBoundary
  {:id s/Uuid
   :location s/Str})

(s/defschema CardMinimapViewport
  {:id s/Uuid
   :location s/Str})

(s/defschema User
  {:id s/Uuid
   :sub s/Str
   ;; TODO: filter the user attributes in the API layer, to avoid a validation error if the DB contains some unexpected attribute
   (s/optional-key :name) s/Str
   (s/optional-key :nickname) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :emailVerified) s/Bool
   (s/optional-key :picture) s/Str})

(s/defschema Congregation
  {:id (s/conditional
        string? (s/eq "demo")
        :else s/Uuid)
   :name s/Str
   :permissions {s/Keyword (s/eq true)}
   (s/optional-key :loansCsvUrl) s/Str
   :territories [Territory]
   :regions [Region]
   :congregationBoundaries [CongregationBoundary]
   :cardMinimapViewports [CardMinimapViewport]
   :users [User]})

(s/defschema CongregationSummary
  {:id s/Uuid
   :name s/Str})

;; camelCase keys are easier to use from JavaScript than kebab-case
(def ^:private format-key-for-api (memoize (comp csk/->camelCaseKeyword #(str/replace % "?" "") name)))

(defn format-for-api [m]
  (let [f (fn [x]
            (if (map? x)
              (map-keys format-key-for-api x)
              x))]
    (clojure.walk/postwalk f m)))

(defn require-logged-in! []
  (if-not (auth/logged-in?)
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
  (auth/with-user-from-session request
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
               (auth/with-user-from-session request
                 (format-for-api
                  {:dev (getx config/env :dev)
                   :auth0 {:domain (getx config/env :auth0-domain)
                           :clientId (getx config/env :auth0-client-id)}
                   :supportEmail (when (auth/logged-in?)
                                   (getx config/env :support-email))
                   :demoAvailable (some? (:demo-congregation config/env))
                   :user (when (auth/logged-in?)
                           (fix-user-for-liberator auth/*user*))}))))

(defn- current-user-id []
  (let [id (:user/id auth/*user*)]
    (assert id)
    id))

(defn- state-for-request [request]
  (let [session (:session request)
        state (projections/cached-state)]
    (cond-> state
      (::sudo? session) (congregation/sudo (current-user-id))
      (some? (::opened-shares session)) (share/grant-opened-shares (::opened-shares session)
                                                                   (current-user-id)))))

(def ^:private validate-congregation-list (s/validator [CongregationSummary]))

(defn list-congregations [request]
  (auth/with-user-from-session request
    (require-logged-in!)
    (let [state (state-for-request request)]
      (ok (->> (congregation/get-my-congregations state (current-user-id))
               (map (fn [congregation]
                      {:id (:congregation/id congregation)
                       :name (:congregation/name congregation)}))
               (validate-congregation-list))))))

(defn- enrich-congregation-users [congregation conn]
  (let [user-ids (->> (:congregation/users congregation)
                      (map :user/id))
        users (for [user (user/get-users conn {:ids user-ids})]
                (-> (:user/attributes user)
                    (assoc :id (:user/id user))
                    (assoc :sub (:user/subject user))))]
    (assoc congregation :congregation/users users)))

(def ^:private validate-congregation (s/validator Congregation))

(defn get-congregation [request]
  (auth/with-user-from-session request
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          user-id (current-user-id)
          state (state-for-request request)
          congregation (facade/get-congregation state cong-id user-id)]
      (when-not congregation
        ;; This function must support anonymous access for opened shares.
        ;; If anonymous user cannot see the congregation, first prompt them
        ;; to login before giving the forbidden error.
        ;; TODO: refactor other routes to use this same pattern?
        (require-logged-in!)
        (forbidden! "No congregation access"))
      (db/with-db [conn {:read-only? true}]
        (ok (-> congregation
                (loan/enrich-territory-loans!)
                (enrich-congregation-users conn)
                (format-for-api)
                (validate-congregation)))))))

(defn get-demo-congregation [request]
  (auth/with-user-from-session request
    (require-logged-in!) ; TODO: allow demo for anonymous users?
    (let [cong-id (:demo-congregation config/env)
          user-id (current-user-id)
          state (state-for-request request)
          congregation (facade/get-demo-congregation state cong-id user-id)]
      (when-not congregation
        (forbidden! "No demo congregation"))
      (ok (-> congregation
              (format-for-api)
              (validate-congregation))))))

(defn- enrich-command [command]
  (let [user-id (current-user-id)]
    (-> command
        (assoc :command/time ((:now config/env)))
        (assoc :command/user user-id))))

(defn- dispatch! [conn state command]
  (dispatcher/command! conn state (enrich-command command)))

(defn api-command!
  ([conn state command]
   (api-command! conn state command {:message "OK"}))
  ([conn state command ok-response]
   (let [command (enrich-command command)]
     (try
       (dispatcher/command! conn state command)
       (ok ok-response)
       ;; TODO: move the error handling to router middleware, so that it would cover also other calls to dispatch
       (catch ValidationException e
         (log/warn e "Invalid command:" command)
         (bad-request {:errors (.getErrors e)}))
       (catch NoPermitException e
         (log/warn e "Forbidden command:" command)
         (forbidden {:message "Forbidden"}))
       (catch WriteConflictException e
         (log/warn e "Write conflict:" command)
         (conflict {:message "Conflict"}))
       (catch Throwable t
         ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
         (log/error t (str "Command failed: "
                           (pr-str command)
                           "\n"
                           (pr-str t)))
         (internal-server-error {:message "Internal Server Error"}))))))

(defn create-congregation [request]
  (auth/with-user-from-session request
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
  (auth/with-user-from-session request
    (require-logged-in!)
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          user-id (UUID/fromString (get-in request [:params :userId]))
          state (state-for-request request)]
      (db/with-db [conn {}]
        (api-command! conn state {:command/type :congregation.command/add-user
                                  :congregation/id cong-id
                                  :user/id user-id})))))

(defn set-user-permissions [request]
  (auth/with-user-from-session request
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

(defn save-congregation-settings [request]
  (auth/with-user-from-session request
    (require-logged-in!)
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          name (get-in request [:params :congregationName])
          loans-csv-url (get-in request [:params :loansCsvUrl]) ; TODO
          state (state-for-request request)]
      (db/with-db [conn {}]
        (api-command! conn state {:command/type :congregation.command/rename-congregation
                                  :congregation/id cong-id
                                  :congregation/name name})))))

(defn download-qgis-project [request]
  (auth/with-user-from-session request
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

(defn share-territory-link [request]
  (auth/with-user-from-session request
    (require-logged-in!)
    (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
          territory-id (UUID/fromString (get-in request [:params :territory]))
          state (state-for-request request)
          share-key (share/generate-share-key)]
      (db/with-db [conn {}]
        (api-command! conn state {:command/type :share.command/share-territory-link
                                  :share/id (UUID/randomUUID)
                                  :share/key share-key
                                  :congregation/id cong-id
                                  :territory/id territory-id}
                      {:url (str (:public-url config/env) "/share/" share-key)
                       :key share-key})))))

(defn- refresh-projections! []
  (projections/refresh-async!)
  (projections/await-refreshed (Duration/ofSeconds 10)))

(defn- record-share-opened! [share state]
  (db/with-db [conn {}]
    (dispatch! conn state {:command/type :share.command/record-share-opened
                           :share/id (:share/id share)}))
  (refresh-projections!)) ; XXX: this is a GET request, so it doesn't trigger automatic refresh

(defn open-share [request]
  (auth/with-user-from-session request
    (let [share-key (get-in request [:params :share-key])
          state (state-for-request request)
          share (share/find-share-by-key state share-key)]
      (if share
        (let [session (-> (:session request)
                          (update ::opened-shares conj-set (:share/id share)))]
          (record-share-opened! share state)
          (-> (ok {:congregation (:congregation/id share)
                   :territory (:territory/id share)})
              (assoc :session session)))
        (not-found {:message "Share not found"})))))

(defroutes api-routes
  (GET "/" [] (ok "Territory Bro"))
  (POST "/api/login" request (login request))
  (POST "/api/dev-login" request (dev-login request))
  (POST "/api/logout" [] (logout))
  (GET "/api/sudo" request (sudo request))
  (ANY "/api/settings" [] settings)
  (POST "/api/congregations" request (create-congregation request))
  (GET "/api/congregations" request (list-congregations request))
  (GET "/api/congregation/demo" request (get-demo-congregation request))
  (GET "/api/congregation/:congregation" request (get-congregation request))
  (POST "/api/congregation/:congregation/add-user" request (add-user request))
  (POST "/api/congregation/:congregation/set-user-permissions" request (set-user-permissions request))
  (POST "/api/congregation/:congregation/settings" request (save-congregation-settings request))
  (GET "/api/congregation/:congregation/qgis-project" request (download-qgis-project request))
  (POST "/api/congregation/:congregation/territory/:territory/share" request (share-territory-link request))
  (GET "/api/share/:share-key" request (open-share request)))

(comment
  (db/with-db [conn {:read-only? true}]
    (->> (user/get-users conn)
         (filter #(= "" (:name (:user/attributes %)))))))
