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
            [territory-bro.infra.user :as user])
  (:import (java.util UUID)
           (org.postgresql.util PSQLException)
           (territory_bro NoPermitException ValidationException WriteConflictException)))

(def ^:dynamic *state* nil) ; the state starts empty, so nil is a good default for tests
(def ^:dynamic *conn*) ; unbound var gives a better error message than nil, when forgetting db/with-db

(defn wrap-db-connection [handler]
  (fn [request]
    (db/with-db [conn {}]
      (binding [*conn* conn]
        (handler request)))))

(defn require-logged-in! []
  (when-not (auth/logged-in?)
    (http-response/unauthorized! "Not logged in")))

(defn- super-user? []
  (let [super-users (:super-users config/env)
        user auth/*user*]
    (or (contains? super-users (:user/id user))
        (contains? super-users (:sub user)))))

(defn sudo [session]
  (require-logged-in!)
  (when-not (super-user?)
    (http-response/forbidden! "Not super user"))
  (log/info "Super user promotion")
  (assoc session :territory-bro.api/sudo? true))


(defn- enrich-command [command]
  (let [user-id (auth/current-user-id)]
    (-> command
        (assoc :command/time ((:now config/env)))
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


(defn- enrich-congregation [cong]
  (let [user-id (auth/current-user-id)
        cong-id (:congregation/id cong)]
    (-> cong
        (dissoc :congregation/user-permissions)
        (assoc
         ;; TODO: remove me - query individual permissions as needed
         :congregation/permissions (->> (permissions/list-permissions *state* user-id [cong-id])
                                        (map (fn [permission]
                                               [permission true]))
                                        (into {}))
         ;; TODO: remove me - use list-congregation-users instead
         :congregation/users (for [user-id (congregation/get-users *state* cong-id)]
                               {:user/id user-id})
         ;; TODO: extract query functions
         :congregation/territories (sequence (vals (get-in *state* [::territory/territories cong-id])))
         :congregation/congregation-boundaries (sequence (vals (get-in *state* [::congregation-boundary/congregation-boundaries cong-id])))
         :congregation/regions (sequence (vals (get-in *state* [::region/regions cong-id])))
         :congregation/card-minimap-viewports (sequence (vals (get-in *state* [::card-minimap-viewport/card-minimap-viewports cong-id])))))))

(defn- apply-user-permissions-for-congregation [cong]
  (let [user-id (auth/current-user-id)
        cong-id (:congregation/id cong)
        ;; TODO: move territory fetching to list-territories
        territory-ids (lazy-seq (for [[_ _ territory-id] (permissions/match *state* user-id [:view-territory cong-id '*])]
                                  territory-id))]
    (cond
      ;; TODO: deduplicate with congregation/apply-user-permissions
      (permissions/allowed? *state* user-id [:view-congregation cong-id])
      cong

      ;; TODO: introduce a :view-congregation-temporarily permission when opening shares?
      (not (empty? territory-ids))
      (-> cong
          (assoc :congregation/users [])
          (assoc :congregation/congregation-boundaries [])
          (assoc :congregation/regions [])
          (assoc :congregation/card-minimap-viewports [])
          (assoc :congregation/territories (for [territory-id territory-ids]
                                             (get-in *state* [::territory/territories cong-id territory-id]))))

      :else
      nil)))

(defn get-own-congregation [cong-id]
  (some-> (congregation/get-unrestricted-congregation *state* cong-id)
          (enrich-congregation)
          (apply-user-permissions-for-congregation)))

(defn get-demo-congregation [cong-id]
  (when cong-id
    (some-> (congregation/get-unrestricted-congregation *state* cong-id)
            (enrich-congregation)
            (assoc :congregation/id "demo")
            (assoc :congregation/name "Demo Congregation")
            (assoc :congregation/loans-csv-url nil)
            (assoc :congregation/permissions {:view-congregation true
                                              :share-territory-link true})
            (assoc :congregation/users [])
            (dissoc :congregation/schema-name))))

(defn get-congregation [cong-id]
  (if (= "demo" cong-id)
    (or (get-demo-congregation (:demo-congregation config/env))
        (http-response/not-found! "No demo"))
    (or (get-own-congregation cong-id)
        (require-logged-in!)
        (http-response/forbidden! "No congregation access"))))

(defn list-congregations []
  (let [user-id (auth/current-user-id)]
    (congregation/get-my-congregations *state* user-id)))

(defn list-congregation-users [cong-id]
  (let [user-ids (congregation/get-users *state* cong-id)]
    (vec (for [user (user/get-users *conn* {:ids user-ids})]
           ;; TODO: remove this unnecessary format change, return users as-is
           (-> (:user/attributes user)
               (assoc :id (:user/id user))
               (assoc :sub (:user/subject user)))))))

(defn download-qgis-project [cong-id]
  (let [congregation (get-congregation cong-id)
        gis-user (gis-user/get-gis-user *state* cong-id (auth/current-user-id))]
    (when-not gis-user
      (http-response/forbidden! "No GIS access"))
    {:content (qgis/generate-project {:database-host (:gis-database-host config/env)
                                      :database-name (:gis-database-name config/env)
                                      :database-schema (:congregation/schema-name congregation)
                                      :database-username (:gis-user/username gis-user)
                                      :database-password (:gis-user/password gis-user)
                                      :database-ssl-mode (:gis-database-ssl-mode config/env)})
     :filename (qgis/project-file-name (:congregation/name congregation))}))


(defn- enrich-do-not-calls [territory cong-id territory-id]
  (merge territory
         (-> (do-not-calls/get-do-not-calls *conn* cong-id territory-id)
             (select-keys [:territory/do-not-calls]))))

(defn- apply-user-permissions-for-territory [territory]
  (let [user-id (auth/current-user-id)
        cong-id (:congregation/id territory)
        territory-id (:territory/id territory)]
    (when (or (permissions/allowed? *state* user-id [:view-congregation cong-id])
              (permissions/allowed? *state* user-id [:view-territory cong-id territory-id]))
      territory)))

(defn get-own-territory [cong-id territory-id]
  (some-> (territory/get-unrestricted-territory *state* cong-id territory-id)
          (enrich-do-not-calls cong-id territory-id)
          (apply-user-permissions-for-territory)))

(defn get-demo-territory [cong-id territory-id]
  (when cong-id
    (some-> (territory/get-unrestricted-territory *state* cong-id territory-id)
            (assoc :congregation/id "demo"))))

(defn get-territory [cong-id territory-id]
  (if (= "demo" cong-id)
    (or (get-demo-territory (:demo-congregation config/env) territory-id)
        (http-response/not-found! "No demo"))
    (or (get-own-territory cong-id territory-id)
        (require-logged-in!)
        (http-response/forbidden! "No territory access"))))

(defn list-territories! [cong-id {:keys [fetch-loans?]}]
  ;; TODO: inline get-own-congregation and only get the territories instead of everything in the congregation
  (let [user-id (auth/current-user-id)
        congregation (get-congregation cong-id)
        fetch-loans? (and fetch-loans?
                          (permissions/allowed? *state* user-id [:view-congregation cong-id])
                          (some? (:congregation/loans-csv-url congregation)))]
    (when congregation
      (:congregation/territories (cond-> congregation
                                   fetch-loans? (loan/enrich-territory-loans!))))))


(defn- generate-share-key [territory]
  (let [cong-id (:congregation/id territory)
        territory-id (:territory/id territory)]
    (if (= "demo" cong-id)
      (share/demo-share-key territory-id)
      (let [share-key (share/generate-share-key)]
        (dispatch! {:command/type :share.command/create-share
                    :share/id (UUID/randomUUID)
                    :share/key share-key
                    :share/type :link
                    :congregation/id cong-id
                    :territory/id territory-id})
        share-key))))

(defn share-territory-link [cong-id territory-id]
  (let [territory (get-territory cong-id territory-id)
        share-key (generate-share-key territory)]
    {:url (share/build-share-url share-key (:territory/number territory))
     :key share-key}))

(defn generate-qr-code [cong-id territory-id]
  (let [territory (get-territory cong-id territory-id)
        share-key (generate-share-key territory)]
    {:url (str (:qr-code-base-url config/env) "/" share-key)
     :key share-key}))
