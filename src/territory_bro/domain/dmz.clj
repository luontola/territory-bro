;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.dmz
  (:require [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.loan :as loan]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]
            [territory-bro.gis.gis-user :as gis-user]
            [territory-bro.gis.qgis :as qgis]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.infra.user :as user]
            [territory-bro.infra.util :as util]
            [territory-bro.infra.util :refer [conj-set]]
            [territory-bro.projections :as projections])
  (:import (java.util UUID)
           (org.postgresql.util PSQLException)
           (territory_bro NoPermitException ValidationException WriteConflictException)))

;;;; State

(def ^:dynamic *state* nil) ; the state starts empty, so nil is a good default for tests

(defn enrich-state-for-request [state request]
  (let [session (:session request)
        user-id (auth/current-user-id)]
    (-> state
        ;; default permissions for all users
        (permissions/grant user-id [:view-congregation "demo"])
        (permissions/grant user-id [:share-territory-link "demo"])
        ;; custom permissions based on user session
        (cond->
          (::sudo? session) (congregation/sudo user-id)
          (some? (::opened-shares session)) (share/grant-opened-shares (::opened-shares session) user-id)))))

(defn- state-for-request [request]
  (-> (projections/cached-state)
      (enrich-state-for-request request)))

(defn wrap-current-state [handler]
  (fn [request]
    (binding [*state* (state-for-request request)]
      (handler request))))


;;;; Database connection

(def ^:dynamic *conn*) ; unbound var gives a better error message than nil, when forgetting db/with-db

(defn wrap-db-connection [handler]
  (fn [request]
    (db/with-transaction [conn {}]
      (binding [*conn* conn]
        (handler request)))))


;;;; Authorization

(defn require-logged-in! []
  (when-not (auth/logged-in?)
    (http-response/unauthorized! "Not logged in")))

(defn access-denied! []
  (require-logged-in!) ; if the user is not logged in, first prompt them to log in - they might have access after logging in
  (http-response/forbidden! "Access denied"))

(defn wrap-access-check [handler pred]
  (fn [request]
    (let [cong-id (get-in request [:path-params :congregation])]
      (when-not (pred cong-id)
        (access-denied!))
      (handler request))))

(defn allowed? [permit]
  (permissions/allowed? *state* (auth/current-user-id) permit))

(defn view-territory? [cong-id territory-id]
  (or (allowed? [:view-congregation cong-id]) ; :view-congregation implies :view-territory for all territories
      (allowed? [:view-territory cong-id territory-id])))

(defn view-printouts-page? [cong-id]
  (allowed? [:view-congregation cong-id]))

(defn view-settings-page? [cong-id]
  (or (allowed? [:configure-congregation cong-id])
      (allowed? [:gis-access cong-id])))


;;;; Authentication

(defn- super-user? []
  (let [super-users (:super-users config/env)
        user auth/*user*]
    (or (contains? super-users (:user/id user))
        (contains? super-users (:sub user)))))

(defn sudo [session]
  (when-not (super-user?)
    (access-denied!))
  (log/info "Super user promotion")
  (assoc session ::sudo? true))

(defn ^:dynamic save-user-from-jwt! [jwt]
  (user/save-user! *conn* (:sub jwt) (select-keys jwt auth/user-profile-keys)))


;;;; Commands

(defn- enrich-command [command]
  (let [user-id (auth/current-user-id)]
    (-> command
        (assoc :command/time (config/now))
        (assoc :command/user user-id))))

(defn dispatch! [command]
  (let [command (enrich-command command)]
    (try
      (dispatcher/command! *conn* *state* command)
      ;; TODO: catch the exceptions and convert them to http responses at a higher level?
      (catch ValidationException e
        (log/warn e "Invalid command:" command)
        (http-response/bad-request! {:errors (.getErrors e)}))
      (catch NoPermitException e
        (log/warn e "Forbidden command:" command)
        (http-response/forbidden! "Forbidden"))
      (catch WriteConflictException e
        (log/warn e "Write conflict:" command)
        (http-response/conflict! "Conflict"))
      (catch PSQLException e
        ;; TODO: consider converting to WriteConflictException inside the event store
        (when (= db/psql-deadlock-detected (.getSQLState e))
          (log/warn e "Deadlock detected:" command)
          (http-response/conflict! "Conflict"))
        (log/warn e "Database error:" command
                  "\nErrorCode:" (.getErrorCode e)
                  "\nSQLState:" (.getSQLState e)
                  "\nServerErrorMessage:" (.getServerErrorMessage e))
        (http-response/internal-server-error! "Internal Server Error"))
      (catch Throwable t
        ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
        (log/error t (str "Command failed: "
                          (pr-str command)
                          "\n"
                          (pr-str t)))
        (http-response/internal-server-error! "Internal Server Error")))))


;;;; Congregations

(defn- coerce-demo-cong-id [cong-id]
  (if (= "demo" cong-id)
    (or (:demo-congregation config/env)
        (http-response/not-found! "No demo"))
    cong-id))

(defn- filter-congregation [congregation]
  (dissoc congregation :congregation/user-permissions))

(defn get-congregation [cong-id]
  (when-not (or (allowed? [:view-congregation cong-id])
                (allowed? [:view-congregation-temporarily cong-id]))
    (access-denied!))
  (let [congregation (-> (congregation/get-unrestricted-congregation *state* (coerce-demo-cong-id cong-id))
                         (filter-congregation))]
    (if (= "demo" cong-id)
      (-> congregation
          (assoc :congregation/id "demo")
          (assoc :congregation/name "Demo Congregation")
          (dissoc :congregation/loans-csv-url)
          (dissoc :congregation/schema-name))
      congregation)))

(def ^:private permits->congregations
  (let [permits->cong-ids (comp (map (fn [[_ cong-id]] cong-id))
                                (filter #(not= "demo" %))) ; everybody has demo permissions by default
        cong-ids->congregations (comp (map #(congregation/get-unrestricted-congregation *state* %))
                                      (map filter-congregation))]
    (comp permits->cong-ids
          cong-ids->congregations)))

(defn list-congregations []
  (->> (permissions/match *state* (auth/current-user-id) [:view-congregation '*])
       (into [] permits->congregations)
       (util/natural-sort-by :congregation/name)))


;;;; Settings

(defn list-congregation-users [cong-id]
  (when (allowed? [:view-congregation cong-id])
    (let [user-ids (congregation/get-users *state* cong-id)]
      (user/get-users *conn* {:ids user-ids}))))

(defn download-qgis-project [cong-id]
  (when-not (allowed? [:gis-access cong-id])
    (access-denied!))
  (let [congregation (get-congregation cong-id)
        gis-user (gis-user/get-gis-user *state* cong-id (auth/current-user-id))]
    {:content (qgis/generate-project {:database-host (:gis-database-host config/env)
                                      :database-name (:gis-database-name config/env)
                                      :database-schema (:congregation/schema-name congregation)
                                      :database-username (:gis-user/username gis-user)
                                      :database-password (:gis-user/password gis-user)
                                      :database-ssl-mode (:gis-database-ssl-mode config/env)})
     :filename (qgis/project-file-name (:congregation/name congregation))}))


;;;; Territories

(defn get-territory [cong-id territory-id]
  (when-not (view-territory? cong-id territory-id)
    (access-denied!))
  (let [territory (territory/get-unrestricted-territory *state* (coerce-demo-cong-id cong-id) territory-id)]
    (if (= "demo" cong-id)
      (assoc territory :congregation/id "demo")
      territory)))

(defn get-do-not-calls [cong-id territory-id]
  (when (and (view-territory? cong-id territory-id)
             (not= "demo" cong-id))
    (:territory/do-not-calls (do-not-calls/get-do-not-calls *conn* cong-id territory-id))))

(defn list-territories [cong-id]
  (->> (cond
         (allowed? [:view-congregation cong-id])
         (vals (get-in *state* [::territory/territories (coerce-demo-cong-id cong-id)]))

         (allowed? [:view-congregation-temporarily cong-id])
         (for [[_ _ territory-id] (permissions/match *state* (auth/current-user-id) [:view-territory cong-id '*])]
           (get-in *state* [::territory/territories cong-id territory-id])))
       (util/natural-sort-by :territory/number)))

(defn enrich-territory-loans [cong-id territories]
  (if (allowed? [:view-congregation cong-id])
    (if-some [loans-csv-url (:congregation/loans-csv-url (get-congregation cong-id))]
      (loan/enrich-territory-loans! territories loans-csv-url)
      territories)
    territories))


;;;; Shares

(defn- generate-share-key [territory share-type]
  (let [cong-id (:congregation/id territory)
        territory-id (:territory/id territory)]
    (if (= "demo" cong-id)
      (share/demo-share-key territory-id)
      (let [share-key (share/generate-share-key)]
        (dispatch! {:command/type :share.command/create-share
                    :share/id (UUID/randomUUID)
                    :share/key share-key
                    :share/type share-type
                    :congregation/id cong-id
                    :territory/id territory-id})
        share-key))))

(defn share-territory-link [cong-id territory-id]
  (when-not (allowed? [:share-territory-link cong-id territory-id])
    (access-denied!))
  (let [territory (get-territory cong-id territory-id)
        share-key (generate-share-key territory :link)]
    {:url (share/build-share-url share-key (:territory/number territory))
     :key share-key}))

(defn generate-qr-code [cong-id territory-id]
  (when-not (allowed? [:share-territory-link cong-id territory-id])
    (access-denied!))
  (let [territory (get-territory cong-id territory-id)
        share-key (generate-share-key territory :qr-code)]
    {:url (share/build-qr-code-url share-key)
     :key share-key}))

(defn open-share! [share-key session]
  (let [share (share/find-share-by-key *state* share-key)
        demo-territory (share/demo-share-key->territory-id share-key)]
    (cond
      (some? share)
      (let [session (update session ::opened-shares conj-set (:share/id share))]
        (dispatch! {:command/type :share.command/record-share-opened
                    :share/id (:share/id share)})
        [share session])

      (some? demo-territory)
      [{:congregation/id "demo"
        :territory/id demo-territory}
       nil])))

(defn open-share-without-cookies [state cong-id territory-id share-key]
  (let [share (share/find-share-by-key *state* share-key)]
    (if (and (some? share)
             (= cong-id (:congregation/id share))
             (= territory-id (:territory/id share)))
      (share/grant-opened-shares state [(:share/id share)] (auth/current-user-id))
      state)))


;;;; Other geometries

(defn get-congregation-boundary [cong-id]
  (when (allowed? [:view-congregation cong-id])
    (get-in *state* [::congregation-boundary/congregation-boundary (coerce-demo-cong-id cong-id)])))

(defn list-regions [cong-id]
  (when (allowed? [:view-congregation cong-id])
    (->> (vals (get-in *state* [::region/regions (coerce-demo-cong-id cong-id)]))
         (util/natural-sort-by :region/name))))

(defn list-card-minimap-viewports [cong-id]
  (when (allowed? [:view-congregation cong-id])
    (vals (get-in *state* [::card-minimap-viewport/card-minimap-viewports (coerce-demo-cong-id cong-id)]))))
