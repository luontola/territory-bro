;; Copyright © 2015-2024 Esko Luontola
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
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.loan :as loan]
            [territory-bro.domain.share :as share]
            [territory-bro.gis.gis-user :as gis-user]
            [territory-bro.gis.qgis :as qgis]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.event-store :as event-store]
            [territory-bro.infra.jwt :as jwt]
            [territory-bro.infra.user :as user]
            [territory-bro.infra.util :refer [conj-set getx]]
            [territory-bro.projections :as projections])
  (:import (com.auth0.jwt.exceptions JWTVerificationException)
           (java.time Duration)
           (java.util UUID)
           (org.postgresql.util PSQLException)
           (territory_bro NoPermitException ValidationException WriteConflictException)))

(s/defschema Territory
  {:id s/Uuid
   :number s/Str
   :addresses s/Str
   :region s/Str
   :meta {s/Keyword s/Any}
   :location s/Str
   (s/optional-key :doNotCalls) s/Str
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
   :loansCsvUrl (s/maybe s/Str)
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
  (require-logged-in!)
  (if (super-user?)
    (let [session (-> (:session request)
                      (assoc ::sudo? true))]
      (log/info "Super user promotion")
      (-> (see-other "/")
          (assoc :session session)))
    (forbidden "Not super user")))

(defn- fix-user-for-liberator [user]
  ;; TODO: custom serializer for UUID
  (if (some? (:user/id user))
    (update user :user/id str)
    user))

(defresource settings
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [_request]}]
               (format-for-api
                {:dev (getx config/env :dev)
                 :auth0 {:domain (getx config/env :auth0-domain)
                         :clientId (getx config/env :auth0-client-id)}
                 :supportEmail (when (auth/logged-in?)
                                 (getx config/env :support-email))
                 :demoAvailable (some? (:demo-congregation config/env))
                 :user (when (auth/logged-in?)
                         (fix-user-for-liberator auth/*user*))})))

(defn current-user-id []
  (let [id (:user/id auth/*user*)]
    (assert id)
    id))

(defn enrich-state-for-request [state request]
  (let [session (:session request)]
    (cond-> state
      (::sudo? session) (congregation/sudo (current-user-id))
      (some? (::opened-shares session)) (share/grant-opened-shares (::opened-shares session)
                                                                   (current-user-id)))))

(defn state-for-request [request]
  (let [state (projections/cached-state)]
    (enrich-state-for-request state request)))

(defn- current-state-for-request [conn request]
  (let [state (projections/current-state conn)]
    (enrich-state-for-request state request)))


(defn list-congregations [request]
  (let [state (state-for-request request)]
    (ok (->> (congregation/get-my-congregations state (current-user-id))
             (mapv (fn [congregation]
                     (select-keys congregation [:congregation/id :congregation/name])))))))

(defn enrich-congregation-users [congregation conn]
  (let [user-ids (->> (:congregation/users congregation)
                      (map :user/id))
        users (for [user (user/get-users conn {:ids user-ids})]
                (-> (:user/attributes user)
                    (assoc :id (:user/id user))
                    (assoc :sub (:user/subject user))))]
    (assoc congregation :congregation/users users)))

(defn ^:dynamic get-congregation [request {:keys [fetch-loans?]}]
  (let [cong-id (get-in request [:path-params :congregation])
        user-id (current-user-id)
        state (state-for-request request)
        congregation (dmz/get-own-congregation state cong-id user-id)
        permissions (:congregation/permissions congregation)]
    (when-not congregation
      ;; This function must support anonymous access for opened shares.
      ;; If anonymous user cannot see the congregation, first prompt them
      ;; to login before giving the forbidden error.
      ;; TODO: refactor other routes to use this same pattern?
      (require-logged-in!)
      (forbidden! "No congregation access"))
    (db/with-db [conn {:read-only? true}]
      (ok (-> congregation
              (cond-> (and fetch-loans?
                           (:view-congregation permissions))
                      (loan/enrich-territory-loans!))
              (enrich-congregation-users conn))))))

(defn get-demo-congregation [request]
  ;; anonymous access is allowed
  (let [cong-id (:demo-congregation config/env)
        user-id (current-user-id)
        state (state-for-request request)
        congregation (dmz/get-demo-congregation state cong-id user-id)]
    (when-not congregation
      (forbidden! "No demo congregation"))
    (ok congregation)))

(defn get-territory [request]
  (db/with-db [conn {}]
    (let [cong-id (get-in request [:path-params :congregation])
          territory-id (get-in request [:path-params :territory])
          user-id (current-user-id)
          state (state-for-request request)
          territory (dmz/get-own-territory conn state cong-id territory-id user-id)]
      (when-not territory
        ;; This function must support anonymous access for opened shares.
        ;; If anonymous user cannot see the congregation, first prompt them
        ;; to login before giving the forbidden error.
        (require-logged-in!)
        (forbidden! "No territory access"))
      (ok (-> territory
              (dissoc :congregation/id))))))

(defn get-demo-territory [request]
  ;; anonymous access is allowed
  (let [cong-id (:demo-congregation config/env)
        territory-id (get-in request [:path-params :territory])
        state (state-for-request request)
        territory (dmz/get-demo-territory state cong-id territory-id)]
    (when-not territory
      (forbidden! "No demo congregation"))
    (ok (-> territory
            (dissoc :congregation/id)))))

(defn- enrich-command [command]
  (let [user-id (current-user-id)]
    (-> command
        (assoc :command/time ((:now config/env)))
        (assoc :command/user user-id))))

(defn dispatch! [conn state command]
  (let [command (enrich-command command)]
    (try
      (dispatcher/command! conn state command)
      (catch ValidationException e
        (log/warn e "Invalid command:" command)
        (bad-request! {:errors (.getErrors e)}))
      (catch NoPermitException e
        (log/warn e "Forbidden command:" command)
        (forbidden! {:message "Forbidden"}))
      (catch WriteConflictException e
        (log/warn e "Write conflict:" command)
        (conflict! {:message "Conflict"}))
      (catch PSQLException e
        ;; TODO: consider converting to WriteConflictException inside the event store
        (when (= db/psql-deadlock-detected (.getSQLState e))
          (log/warn e "Deadlock detected:" command)
          (conflict! {:message "Conflict"}))
        (log/warn e "Database error:" command
                  "\nErrorCode:" (.getErrorCode e)
                  "\nSQLState:" (.getSQLState e)
                  "\nServerErrorMessage:" (.getServerErrorMessage e))
        (internal-server-error! {:message "Internal Server Error"}))
      (catch Throwable t
        ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
        (log/error t (str "Command failed: "
                          (pr-str command)
                          "\n"
                          (pr-str t)))
        (internal-server-error! {:message "Internal Server Error"})))))

(def ok-body {:message "OK"})

(defn create-congregation [request]
  (require-logged-in!)
  (let [cong-id (UUID/randomUUID)
        name (get-in request [:params :name])
        state (state-for-request request)]
    (db/with-db [conn {}]
      (dispatch! conn state {:command/type :congregation.command/create-congregation
                             :congregation/id cong-id
                             :congregation/name name})
      (ok {:id cong-id}))))

(defn add-user [request]
  (require-logged-in!)
  (let [cong-id (get-in request [:path-params :congregation])
        user-id (or (parse-uuid (get-in request [:params :user-id]))
                    (throw (ValidationException. [[:invalid-user-id]])))
        state (state-for-request request)]
    (db/with-db [conn {}]
      (dispatch! conn state {:command/type :congregation.command/add-user
                             :congregation/id cong-id
                             :user/id user-id})
      (ok ok-body))))

(defn set-user-permissions [request]
  (let [cong-id (get-in request [:path-params :congregation])
        user-id (UUID/fromString (get-in request [:params :user-id]))
        permissions (->> (get-in request [:params :permissions])
                         (map keyword))
        state (state-for-request request)]
    (db/with-db [conn {}]
      (dispatch! conn state {:command/type :congregation.command/set-user-permissions
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/ids permissions})
      (ok ok-body))))

(defn save-congregation-settings [request]
  (require-logged-in!)
  (let [cong-id (get-in request [:path-params :congregation])
        name (get-in request [:params :congregation-name])
        loans-csv-url (get-in request [:params :loans-csv-url])
        state (state-for-request request)]
    (db/with-db [conn {}]
      (dispatch! conn state {:command/type :congregation.command/update-congregation
                             :congregation/id cong-id
                             :congregation/name name
                             :congregation/loans-csv-url loans-csv-url})
      (ok ok-body))))

(defn download-qgis-project [request]
  (require-logged-in!)
  (let [cong-id (get-in request [:path-params :congregation])
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
          (response/header "Content-Disposition" (str "attachment; filename=\"" file-name "\""))))))

(defn edit-do-not-calls [request]
  (require-logged-in!)
  (let [cong-id (get-in request [:path-params :congregation])
        territory-id (get-in request [:path-params :territory])
        do-not-calls (str/trim (str (get-in request [:params :do-not-calls])))
        state (state-for-request request)]
    (db/with-db [conn {}]
      (dispatch! conn state {:command/type :do-not-calls.command/save-do-not-calls
                             :congregation/id cong-id
                             :territory/id territory-id
                             :territory/do-not-calls do-not-calls})
      (ok {}))))

(defn share-territory-link [request]
  (if (= "demo" (get-in request [:path-params :congregation]))
    (let [cong-id (:demo-congregation config/env)
          territory-id (get-in request [:path-params :territory])
          state (state-for-request request)
          share-key (share/demo-share-key territory-id)
          territory (dmz/get-demo-territory state cong-id territory-id)]
      (ok {:url (share/build-share-url share-key (:territory/number territory))
           :key share-key}))
    (do
      (require-logged-in!)
      (let [cong-id (get-in request [:path-params :congregation])
            territory-id (get-in request [:path-params :territory])
            state (state-for-request request)
            share-key (share/generate-share-key)
            territory (dmz/get-demo-territory state cong-id territory-id)]
        (db/with-db [conn {}]
          (dispatch! conn state {:command/type :share.command/create-share
                                 :share/id (UUID/randomUUID)
                                 :share/key share-key
                                 :share/type :link
                                 :congregation/id cong-id
                                 :territory/id territory-id})
          (ok {:url (share/build-share-url share-key (:territory/number territory))
               :key share-key}))))))

(defn generate-qr-code! [conn request cong-id territory-id]
  (let [share-key (share/generate-share-key)
        _ (event-store/acquire-write-lock! conn)
        state (current-state-for-request conn request)]
    (dispatch! conn state {:command/type :share.command/create-share
                           :share/id (UUID/randomUUID)
                           :share/key share-key
                           :share/type :qr-code
                           :congregation/id cong-id
                           :territory/id territory-id})
    share-key))

(defn generate-qr-codes [request]
  (if (= "demo" (get-in request [:path-params :congregation]))
    (let [territory-ids (->> (get-in request [:params :territories])
                             (mapv UUID/fromString))
          shares (into [] (for [territory-id territory-ids]
                            (let [share-key (share/demo-share-key territory-id)]
                              {:territory territory-id
                               :key share-key
                               :url (str (:qr-code-base-url config/env) "/" share-key)})))]
      (ok {:qrCodes shares}))
    (do
      (require-logged-in!)
      (let [cong-id (get-in request [:path-params :congregation])
            territory-ids (->> (get-in request [:params :territories])
                               (mapv UUID/fromString))]
        (db/with-db [conn {}]
          ;; TODO: the ::share-cache can be removed after removing the React site, because HTMX uses the browser's cache
          (let [share-cache (or (-> request :session ::share-cache)
                                {})
                shares (into [] (for [territory-id territory-ids]
                                  (let [share-key (or (get share-cache territory-id)
                                                      (generate-qr-code! conn request cong-id territory-id))]
                                    {:territory territory-id
                                     :key share-key
                                     :url (str (:qr-code-base-url config/env) "/" share-key)})))
                share-cache (->> shares
                                 (map (juxt :territory :key))
                                 (into share-cache))]
            (-> (ok {:qrCodes shares})
                (assoc :session (-> (:session request)
                                    (assoc ::share-cache share-cache))))))))))

(defn- refresh-projections! []
  (projections/refresh-async!)
  (projections/await-refreshed (Duration/ofSeconds 10)))

(defn- record-share-opened! [share state]
  (db/with-db [conn {}]
    (dispatch! conn state {:command/type :share.command/record-share-opened
                           :share/id (:share/id share)}))
  (refresh-projections!)) ; XXX: this is a GET request, so it doesn't trigger automatic refresh

(defn open-share [request]
  (let [share-key (get-in request [:path-params :share-key])
        state (state-for-request request)
        share (share/find-share-by-key state share-key)
        demo-territory (share/demo-share-key->territory-id share-key)]
    (cond
      (some? share)
      (let [session (-> (:session request)
                        (update ::opened-shares conj-set (:share/id share)))]
        (record-share-opened! share state)
        (-> (ok {:congregation (:congregation/id share)
                 :territory (:territory/id share)})
            (assoc :session session)))

      (some? demo-territory)
      (ok {:congregation "demo"
           :territory demo-territory})

      :else
      (not-found {:message "Share not found"}))))

(defn- try-parse-uuid [s]
  (when s
    (or (parse-uuid s)
        s)))

(defn compat [request]
  ;; XXX: Compatibility with the new UI's request parameters, until this API is removed.
  ;;      This cannot be done using middleware, because compojure.core/defroutes parses the URL,
  ;;      so there is no spot for middleware between Compojure and the handler function.
  (assoc request
         :path-params (-> (:params request)
                          (select-keys [:congregation :territory :share-key])
                          (update :congregation try-parse-uuid)
                          (update :territory try-parse-uuid))))

(defroutes api-routes
  (POST "/api/login" request (login (compat request)))
  (GET "/api/dev-login" request (dev-login (compat request)))
  (POST "/api/dev-login" request (dev-login (compat request)))
  (POST "/api/logout" [] (logout))
  (GET "/api/sudo" request (sudo (compat request)))
  (ANY "/api/settings" [] settings)
  (POST "/api/congregations" request (create-congregation (compat request)))
  (GET "/api/congregations" request (list-congregations (compat request)))
  (GET "/api/congregation/demo" request (get-demo-congregation (compat request)))
  (GET "/api/congregation/demo/territory/:territory" request (get-demo-territory (compat request)))
  (GET "/api/congregation/:congregation" request (get-congregation (compat request) {:fetch-loans? true}))
  (POST "/api/congregation/:congregation/add-user" request (add-user (compat request)))
  (POST "/api/congregation/:congregation/set-user-permissions" request (set-user-permissions (compat request)))
  (POST "/api/congregation/:congregation/settings" request (save-congregation-settings (compat request)))
  (GET "/api/congregation/:congregation/qgis-project" request (download-qgis-project (compat request)))
  (POST "/api/congregation/:congregation/generate-qr-codes" request (generate-qr-codes (compat request)))
  (GET "/api/congregation/:congregation/territory/:territory" request (get-territory (compat request)))
  (POST "/api/congregation/:congregation/territory/:territory/do-not-calls" request (edit-do-not-calls (compat request)))
  (POST "/api/congregation/:congregation/territory/:territory/share" request (share-territory-link (compat request)))
  (GET "/api/share/:share-key" request (open-share (compat request))))

(comment
  (db/with-db [conn {:read-only? true}]
    (->> (user/get-users conn)
         (filter #(= "" (:name (:user/attributes %)))))))
