;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.api-test
  (:require [clj-http.client :as http]
            [clj-xpath.core :as xpath]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [ring.util.http-predicates :refer :all]
            [territory-bro.api :as api]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.gis.gis-sync :as gis-sync]
            [territory-bro.gis.gis-user :as gis-user]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.jwt :as jwt]
            [territory-bro.infra.jwt-test :as jwt-test]
            [territory-bro.infra.router :as router]
            [territory-bro.infra.user :as user]
            [territory-bro.projections :as projections]
            [territory-bro.test.fixtures :refer [api-fixture db-fixture]])
  (:import (clojure.lang ExceptionInfo)
           (java.time Duration Instant)
           (java.util UUID)
           (org.postgresql.util PSQLException PSQLState)
           (territory_bro NoPermitException ValidationException WriteConflictException)))

(use-fixtures :once (join-fixtures [db-fixture api-fixture]))

(defn- get-cookies [response]
  (->> (get-in response [:headers "Set-Cookie"])
       (map (fn [header]
              (let [[name value] (-> (first (str/split header #";" 2))
                                     (str/split #"=" 2))]
                [name {:value value}])))
       (into {})))

(deftest get-cookies-test
  (is (= {} (get-cookies {})))
  (is (= {"ring-session" {:value "123"}}
         (get-cookies {:headers {"Set-Cookie" ["ring-session=123"]}})))
  (is (= {"ring-session" {:value "123"}}
         (get-cookies {:headers {"Set-Cookie" ["ring-session=123;Path=/;HttpOnly;SameSite=Strict"]}})))
  (is (= {"foo" {:value "123"}
          "bar" {:value "456"}}
         (get-cookies {:headers {"Set-Cookie" ["foo=123" "bar=456"]}}))))

(defn parse-json [body]
  (cond
    (nil? body) body
    (string? body) (json/read-value body)
    :else (json/read-value (slurp (io/reader body)))))

(defn read-body [response]
  (let [content-type (get-in response [:headers "Content-Type"])]
    (if (str/starts-with? content-type "application/json")
      (update response :body parse-json)
      response)))

(defn app [request]
  (-> request router/app read-body))

(defn assert-response [response predicate]
  (assert (predicate response)
          (str "Unexpected response " response))
  response)

(defn refresh-projections! []
  ;; TODO: do synchronously, or propagate errors from the async thread?
  ;;           - GIS changes cannot be synced in test thread, because
  ;;             it will produce transaction conflicts with the worker
  ;;             thread which is triggered by DB notifications
  ;; TODO: propagating errors might be useful also in production
  (projections/refresh-async!)
  (projections/await-refreshed (Duration/ofSeconds 10)))

(defn sync-gis-changes! []
  (gis-sync/refresh-async!)
  (gis-sync/await-refreshed (Duration/ofSeconds 10))
  (projections/await-refreshed (Duration/ofSeconds 10)))

(defn current-state []
  (refresh-projections!)
  (projections/cached-state))


;;;; API Helpers

(defn get-session [response]
  {:cookies (get-cookies response)})

(defn login! [app]
  (let [response (-> (request :post "/api/login")
                     (json-body {:idToken jwt-test/token})
                     app
                     (assert-response ok?))]
    (get-session response)))

(defn logout! [app session]
  (-> (request :post "/api/logout")
      (merge session)
      app
      (assert-response ok?)))

(defn get-user-id [session]
  (let [response (-> (request :get "/api/settings")
                     (merge session)
                     app
                     (assert-response ok?))
        user-id (UUID/fromString (:id (:user (:body response))))]
    user-id))

(defn try-create-congregation! [session name]
  (-> (request :post "/api/congregations")
      (json-body {:name name})
      (merge session)
      app))

(defn create-congregation! [session name]
  (let [response (-> (try-create-congregation! session name)
                     (assert-response ok?))
        cong-id (UUID/fromString (:id (:body response)))]
    cong-id))

(defn try-rename-congregation! [session cong-id name]
  (-> (request :post (str "/api/congregation/" cong-id "/settings"))
      (json-body {:congregationName name
                  :loansCsvUrl ""})
      (merge session)
      app))

(defn try-add-user! [session cong-id user-id]
  (-> (request :post (str "/api/congregation/" cong-id "/add-user"))
      (json-body {:userId (str user-id)})
      (merge session)
      app))

(defn try-edit-do-not-calls! [session cong-id territory-id]
  (-> (request :post (str "/api/congregation/" cong-id "/territory/" territory-id "/do-not-calls"))
      (json-body {:do-not-calls "edited"})
      (merge session)
      app))

(defn create-congregation-without-user! [name]
  (let [cong-id (UUID/randomUUID)
        state (projections/cached-state)]
    (db/with-db [conn {}]
      (dispatcher/command! conn state
                           {:command/type :congregation.command/create-congregation
                            :command/time (Instant/now)
                            :command/system "test"
                            :congregation/id cong-id
                            :congregation/name name}))
    (refresh-projections!)
    cong-id))

(defn revoke-access-from-all! [cong-id]
  (let [state (projections/cached-state)
        user-ids (congregation/get-users state cong-id)]
    (db/with-db [conn {}]
      (doseq [user-id user-ids]
        (dispatcher/command! conn state
                             {:command/type :congregation.command/set-user-permissions
                              :command/time (Instant/now)
                              :command/system "test"
                              :congregation/id cong-id
                              :user/id user-id
                              :permission/ids []})))
    (refresh-projections!)))

(defn create-territory! [cong-id]
  (let [territory-id (UUID/randomUUID)]
    (db/with-db [conn {}]
      (dispatcher/command! conn (projections/cached-state)
                           {:command/type :territory.command/define-territory
                            :command/time (Instant/now)
                            :command/system "test"
                            :congregation/id cong-id
                            :territory/id territory-id
                            :territory/number "123"
                            :territory/addresses "the addresses"
                            :territory/region "the region"
                            :territory/meta {:foo "bar"}
                            :territory/location testdata/wkt-multi-polygon}))
    (refresh-projections!)
    (db/with-db [conn {}]
      (dispatcher/command! conn (projections/cached-state)
                           {:command/type :do-not-calls.command/save-do-not-calls
                            :command/time (Instant/now)
                            :command/system "test"
                            :congregation/id cong-id
                            :territory/id territory-id
                            :territory/do-not-calls "the do-not-calls"}))
    territory-id))

(defn create-share! [cong-id territory-id share-key]
  (db/with-db [conn {}]
    (dispatcher/command! conn (projections/cached-state)
                         {:command/type :share.command/create-share
                          :command/time (Instant/now)
                          :command/system "test"
                          :share/id (UUID/randomUUID)
                          :share/key share-key
                          :share/type :link
                          :congregation/id cong-id
                          :territory/id territory-id}))
  (refresh-projections!))

(defn- parse-qgis-datasource [datasource]
  (let [required (fn [v]
                   (if (nil? v)
                     (throw (AssertionError. (str "Failed to parse: " datasource)))
                     v))
        [_ dbname] (required (re-find #"dbname='(.*?)'" datasource))
        [_ host] (required (re-find #"host=(\S*)" datasource))
        [_ port] (required (re-find #"port=(\S*)" datasource))
        [_ user] (required (re-find #"user='(.*?)'" datasource))
        [_ password] (required (re-find #"password='(.*?)'" datasource))
        [_ schema table] (required (re-find #"table=\"(.*?)\".\"(.*?)\"" datasource))]
    {:dbname dbname
     :host host
     :port (Long/parseLong port)
     :user user
     :password password
     :schema schema
     :table table}))

(deftest parse-qgis-datasource-test
  (is (= {:dbname "territorybro"
          :host "localhost"
          :port 5432
          :user "gis_user_49a5ab7f34234ec4_5dce9e39cf744126"
          :password "pfClEgNVadYRAt-ytE9x4iakLNjwsNGIfLIS6Mc_hfASiYajB8"
          :schema "test_territorybro_49a5ab7f34234ec481b7f45bf9c4d3c2"
          :table "territory"}
         (parse-qgis-datasource "dbname='territorybro' host=localhost port=5432 user='gis_user_49a5ab7f34234ec4_5dce9e39cf744126' password='pfClEgNVadYRAt-ytE9x4iakLNjwsNGIfLIS6Mc_hfASiYajB8' sslmode=prefer key='id' srid=4326 type=MultiPolygon table=\"test_territorybro_49a5ab7f34234ec481b7f45bf9c4d3c2\".\"territory\" (location) sql="))))

(defn get-gis-db-spec [session cong-id]
  (let [response (-> (request :get (str "/api/congregation/" cong-id "/qgis-project"))
                     (merge session)
                     app
                     (assert-response ok?))
        ;; XXX: the XML parser doesn't support DOCTYPE
        qgis-project (str/replace-first (:body response) "<!DOCTYPE qgis PUBLIC 'http://mrcc.com/qgis.dtd' 'SYSTEM'>" "")
        datasource (->> (xpath/$x "//datasource" qgis-project)
                        (map :text)
                        (filter #(str/starts-with? % "dbname="))
                        (map parse-qgis-datasource)
                        (first))]
    {:dbtype "postgresql"
     :dbname (:dbname datasource)
     :host (:host datasource)
     :port (:port datasource)
     :user (:user datasource)
     :password (:password datasource)
     :currentSchema (str (:schema datasource) ",public")}))


;;;; Tests

(deftest format-for-api-test
  (is (= {} (api/format-for-api {})))
  (is (= {:foo 1} (api/format-for-api {:foo 1})))
  (is (= {:fooBar 1} (api/format-for-api {:foo-bar 1})))
  (is (= {:bar 1} (api/format-for-api {:foo/bar 1})))
  (is (= [{:fooBar 1} {:bar 2}] (api/format-for-api [{:foo-bar 1} {:foo/bar 2}]))))

(deftest dispatch-command-test
  (let [conn :dummy-conn
        state :dummy-state
        command {:command/type :dummy-command}
        test-user (UUID. 0 0x123)
        test-time (Instant/ofEpochSecond 42)]
    (binding [auth/*user* {:user/id test-user}
              config/env (assoc config/env :now (constantly test-time))]

      (testing "successful command"
        (with-redefs [dispatcher/command! (fn [& args]
                                            (is (= [conn state (assoc command
                                                                      :command/time test-time
                                                                      :command/user test-user)]
                                                   args))
                                            [{:event/type :dummy-event}])]
          (is (= [{:event/type :dummy-event}]
                 (api/dispatch! conn state command)))))

      (testing "failed command: validation"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (ValidationException. [[:dummy-error 123]])))]
          (is (= {:body {:errors [[:dummy-error 123]]}
                  :status 400
                  :headers {}}
                 (try
                   (api/dispatch! conn state command)
                   (catch ExceptionInfo e
                     (:response (ex-data e))))))))

      (testing "failed command: permission check"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (NoPermitException. test-user [:dummy-permission 123])))]
          (is (= {:body {:message "Forbidden"}
                  :status 403
                  :headers {}}
                 (try
                   (api/dispatch! conn state command)
                   (catch ExceptionInfo e
                     (:response (ex-data e))))))))

      (testing "failed command: write conflict"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (WriteConflictException. "dummy error")))]
          (is (= {:body {:message "Conflict"}
                  :status 409
                  :headers {}}
                 (try
                   (api/dispatch! conn state command)
                   (catch ExceptionInfo e
                     (:response (ex-data e))))))))

      (testing "failed command: PostgreSQL, deadlock detected"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (PSQLException. "dummy error" PSQLState/DEADLOCK_DETECTED)))]
          (is (= {:body {:message "Conflict"}
                  :status 409
                  :headers {}}
                 (try
                   (api/dispatch! conn state command)
                   (catch ExceptionInfo e
                     (:response (ex-data e))))))))

      (testing "failed command: PostgreSQL, any other error"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (PSQLException. "dummy error" PSQLState/UNKNOWN_STATE)))]
          (is (= {:body {:message "Internal Server Error"}
                  :status 500
                  :headers {}}
                 (try
                   (api/dispatch! conn state command)
                   (catch ExceptionInfo e
                     (:response (ex-data e))))))))

      (testing "failed command: internal error"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (RuntimeException. "dummy error")))]
          (is (= {:body {:message "Internal Server Error"}
                  :status 500
                  :headers {}}
                 (try
                   (api/dispatch! conn state command)
                   (catch ExceptionInfo e
                     (:response (ex-data e)))))))))))

(deftest basic-routes-test
  (testing "index"
    (let [response (-> (request :get "/")
                       app)]
      (is (ok? response))))

  (testing "page not found"
    (let [response (-> (request :get "/invalid")
                       app)]
      (is (not-found? response)))))

(deftest login-test
  (testing "login with valid token"
    (let [response (-> (request :post "/api/login")
                       (json-body {:idToken jwt-test/token})
                       app)]
      (is (ok? response))
      (is (= "Logged in" (:body response)))
      (is (= ["ring-session"] (keys (get-cookies response))))))

  (testing "user is saved on login"
    (let [user (db/with-db [conn {}]
                 (user/get-by-subject conn (:sub (jwt/validate jwt-test/token config/env))))]
      (is user)
      (is (= "Esko Luontola" (get-in user [:user/attributes :name])))))

  (testing "login with expired token"
    (binding [config/env (assoc config/env :now #(Instant/now))]
      (let [response (-> (request :post "/api/login")
                         (json-body {:idToken jwt-test/token})
                         app)]
        (is (forbidden? response))
        (is (= "Invalid token" (:body response)))
        (is (empty? (get-cookies response))))))

  (testing "dev login"
    (binding [config/env (assoc config/env :dev true)]
      (let [response (-> (request :post "/api/dev-login")
                         (json-body {:sub "developer"
                                     :name "Developer"
                                     :email "developer@example.com"})
                         app)]
        (is (ok? response))
        (is (= "Logged in" (:body response)))
        (is (= ["ring-session"] (keys (get-cookies response)))))))

  (testing "user is saved on dev login"
    (let [user (db/with-db [conn {}]
                 (user/get-by-subject conn "developer"))]
      (is user)
      (is (= "Developer" (get-in user [:user/attributes :name])))))

  (testing "dev login outside dev mode"
    (let [response (-> (request :post "/api/dev-login")
                       (json-body {:sub "developer"
                                   :name "Developer"
                                   :email "developer@example.com"})
                       app)]
      (is (forbidden? response))
      (is (= "Dev mode disabled" (:body response)))
      (is (empty? (get-cookies response))))))

(deftest dev-login-test
  (testing "authenticates as anybody in dev mode"
    (binding [config/env {:dev true}
              api/save-user-from-jwt! (fn [_]
                                        (UUID. 0 1))]
      (is (= {:status 200,
              :headers {},
              :body "Logged in",
              :session {::auth/user {:user/id (UUID. 0 1)
                                     :sub "sub",
                                     :name "name",
                                     :email "email"}}}
             (api/dev-login {:params {:sub "sub"
                                      :name "name"
                                      :email "email"}})))))

  (testing "is disabled when not in dev mode"
    (binding [config/env {:dev false}]
      (is (= {:status 403
              :headers {}
              :body "Dev mode disabled"}
             (api/dev-login {:params {:sub "sub"
                                      :name "name"
                                      :email "email"}}))))))

(deftest authorization-test
  (testing "before login"
    (let [response (try-create-congregation! nil "test1")]
      (is (unauthorized? response))))

  (let [session (login! app)]
    (testing "after login"
      (let [response (try-create-congregation! session "test2")]
        (is (ok? response))))

    (testing "after logout"
      (logout! app session)
      (let [response (try-create-congregation! session "test3")]
        (is (unauthorized? response))))))

(deftest super-user-test
  (let [cong-id (create-congregation-without-user! "sudo test")
        session (login! app)
        user-id (get-user-id session)]

    (testing "normal users cannot use sudo"
      (let [response (-> (request :get "/api/sudo")
                         (merge session)
                         app)]
        (is (forbidden? response))
        (is (= "Not super user" (:body response)))))

    (testing "super users can use sudo"
      (binding [config/env (update config/env :super-users conj user-id)]
        (let [response (-> (request :get "/api/sudo")
                           (merge session)
                           app)]
          (is (see-other? response))
          (is (= "/" (get-in response [:headers "Location"]))))))

    (testing "super user can view all congregations"
      (let [response (-> (request :get "/api/congregations")
                         (merge session)
                         app)]
        (is (ok? response))
        (is (contains? (->> (:body response)
                            (map :id)
                            (set))
                       (str cong-id)))))

    (testing "super user can configure all congregations"
      (let [response (try-add-user! session cong-id user-id)]
        (is (ok? response))))))

(deftest create-congregation-test
  (let [session (login! app)
        user-id (get-user-id session)
        response (try-create-congregation! session "foo")]
    (is (ok? response))
    (is (:id (:body response)))

    (let [cong-id (UUID/fromString (:id (:body response)))
          state (projections/cached-state)]
      (testing "grants access to the current user"
        (is (= 1 (count (congregation/get-users state cong-id)))))

      (testing "creates a GIS user for the current user"
        (let [gis-user (gis-user/get-gis-user state cong-id user-id)]
          (is gis-user)
          (is (true? (db/with-db [conn {:read-only? true}]
                       (gis-db/user-exists? conn (:gis-user/username gis-user)))))))))

  (testing "requires login"
    (let [response (try-create-congregation! nil "foo")]
      (is (unauthorized? response)))))

(deftest list-congregations-test
  (let [session (login! app)
        cong-id (create-congregation! session "foo")
        response (-> (request :get "/api/congregations")
                     (merge session)
                     app)]
    (is (ok? response))
    (is (sequential? (:body response)))
    (is (contains? (set (:body response))
                   {:id (str cong-id), :name "foo"})))

  (testing "doesn't require login"
    (let [response (-> (request :get "/api/congregations")
                       app)]
      (is (ok? response))
      (is (= [] (:body response))))))

(deftest get-congregation-test
  (let [session (login! app)
        cong-id (create-congregation! session "foo")]

    (testing "get congregation"
      (let [response (-> (request :get (str "/api/congregation/" cong-id))
                         (merge session)
                         app)]
        (is (ok? response))
        (is (= (str cong-id) (:id (:body response))))))

    (testing "requires login"
      (let [response (-> (request :get (str "/api/congregation/" cong-id))
                         app)]
        (is (unauthorized? response))))

    (testing "wrong ID"
      (let [response (-> (request :get (str "/api/congregation/" (UUID/randomUUID)))
                         (merge session)
                         app)]
        ;; same as when ID exists but user has no access
        (is (forbidden? response))
        (is (= "No congregation access" (:body response)))))

    (testing "no access"
      (revoke-access-from-all! cong-id)
      (let [response (-> (request :get (str "/api/congregation/" cong-id))
                         (merge session)
                         app)]
        (is (forbidden? response))
        (is (= "No congregation access" (:body response)))))))

(deftest get-demo-congregation-test
  (let [session (login! app)
        cong-id (create-congregation-without-user! "foo")]
    (binding [config/env (assoc config/env :demo-congregation cong-id)]

      (testing "get demo congregation"
        (let [response (-> (request :get (str "/api/congregation/demo"))
                           (merge session)
                           app)]
          (is (ok? response))
          (is (= {:id "demo"
                  :name "Demo Congregation"
                  :users []
                  :permissions {:viewCongregation true
                                :shareTerritoryLink true}}
                 (select-keys (:body response) [:id :name :users :permissions]))
              "returns an anonymized read-only congregation")))

      (testing "anonymous access is allowed"
        (let [response (-> (request :get (str "/api/congregation/demo"))
                           app)]
          (is (ok? response)))))

    (binding [config/env (assoc config/env :demo-congregation nil)]
      (testing "no demo congregation"
        (let [response (-> (request :get (str "/api/congregation/demo"))
                           (merge session)
                           app)]
          (is (forbidden? response))
          (is (= "No demo congregation" (:body response))))))))

(deftest get-territory-test
  (let [session (login! app)
        cong-id (create-congregation! session "foo")
        territory-id (create-territory! cong-id)]

    (testing "get territory"
      (let [response (-> (request :get (str "/api/congregation/" cong-id "/territory/" territory-id))
                         (merge session)
                         app)]
        (is (ok? response))
        (is (= {:id (str territory-id)
                :doNotCalls "the do-not-calls"}
               (select-keys (:body response) [:id :doNotCalls]))
            "returns full territory information")))

    (testing "requires login"
      (let [response (-> (request :get (str "/api/congregation/" cong-id "/territory/" territory-id))
                         app)]
        (is (unauthorized? response))))

    (testing "wrong ID"
      (let [response (-> (request :get (str "/api/congregation/" cong-id "/territory/" (UUID/randomUUID)))
                         (merge session)
                         app)]
        ;; same as when ID exists but user has no access
        (is (forbidden? response))
        (is (= "No territory access" (:body response)))))

    (testing "no access"
      (revoke-access-from-all! cong-id)
      (let [response (-> (request :get (str "/api/congregation/" cong-id "/territory/" territory-id))
                         (merge session)
                         app)]
        (is (forbidden? response))
        (is (= "No territory access" (:body response)))))))

(deftest get-demo-territory-test
  (let [session-logged-in (login! app)
        cong-id (create-congregation-without-user! "foo")
        territory-id (create-territory! cong-id)]
    (binding [config/env (assoc config/env :demo-congregation cong-id)]

      (testing "get demo territory"
        (let [response (-> (request :get (str "/api/congregation/demo/territory/" territory-id))
                           (merge session-logged-in)
                           app)]
          (is (ok? response))
          (is (= {:id (str territory-id)}
                 (select-keys (:body response) [:id :doNotCalls]))
              "returns an anonymized read-only territory")))

      (testing "anonymous access is allowed"
        (let [response (-> (request :get (str "/api/congregation/demo"))
                           app)]
          (is (ok? response))))

      (testing "get demo territory after opening a share"
        (let [*session-anonymous (atom nil)
              share-key "get-demo-territory-test1"]
          (create-share! cong-id territory-id share-key)

          (testing "- open share"
            (let [response (-> (request :get (str "/api/share/" share-key))
                               (merge @*session-anonymous)
                               app)]
              (reset! *session-anonymous (get-session response)) ; TODO: stateful HTTP client which remembers the cookies automatically
              (is (ok? response))))

          ;; this used to crash in territory-bro.api/current-user-id because territory-bro.infra.authentication/*user* was unbound
          (testing "- view demo territory"
            (let [response (-> (request :get (str "/api/congregation/demo/territory/" territory-id))
                               (merge @*session-anonymous)
                               app)]
              (is (ok? response))
              (is (= (str territory-id)
                     (:id (:body response)))))))))

    (binding [config/env (assoc config/env :demo-congregation nil)]
      (testing "no demo congregation"
        (let [response (-> (request :get (str "/api/congregation/demo/territory/" territory-id))
                           (merge session-logged-in)
                           app)]
          (is (forbidden? response))
          (is (= "No demo congregation" (:body response))))))))

(deftest edit-do-not-calls-test
  (let [session (login! app)
        cong-id (create-congregation! session "foo")
        territory-id (create-territory! cong-id)]

    (testing "edit do-not-calls"
      (let [response (try-edit-do-not-calls! session cong-id territory-id)]
        (is (ok? response)))

      (let [response (-> (request :get (str "/api/congregation/" cong-id "/territory/" territory-id))
                         (merge session)
                         app)]
        (is (ok? response))
        (is (= "edited" (:doNotCalls (:body response))))))

    (testing "no access"
      (revoke-access-from-all! cong-id)
      (let [response (try-edit-do-not-calls! session cong-id territory-id)]
        (is (forbidden? response))))))

(deftest download-qgis-project-test
  (let [session (login! app)
        user-id (get-user-id session)
        cong-id (create-congregation! session "Example Congregation")]

    (let [response (-> (request :get (str "/api/congregation/" cong-id "/qgis-project"))
                       (merge session)
                       app)]
      (is (ok? response))
      (is (str/includes? (:body response) "<qgis"))
      (is (str/includes? (get-in response [:headers "Content-Disposition"])
                         "Example Congregation.qgs")))

    (testing "requires login"
      (let [response (-> (request :get (str "/api/congregation/" cong-id "/qgis-project"))
                         app)]
        (is (unauthorized? response))))

    (testing "requires GIS access"
      (db/with-db [conn {}]
        (dispatcher/command! conn (projections/cached-state)
                             {:command/type :congregation.command/set-user-permissions
                              :command/time (Instant/now)
                              :command/system "test"
                              :congregation/id cong-id
                              :user/id user-id
                              ;; TODO: create a command for removing a single permission? or produce the event directly from tests?
                              ;; removed :gis-access
                              :permission/ids [:view-congregation :configure-congregation]}))
      (refresh-projections!)

      (let [response (-> (request :get (str "/api/congregation/" cong-id "/qgis-project"))
                         (merge session)
                         app)]
        (is (forbidden? response))
        (is (str/includes? (:body response) "No GIS access"))))))

(deftest add-user-test
  (let [session (login! app)
        cong-id (create-congregation! session "Congregation")
        new-user-id (db/with-db [conn {}]
                      (user/save-user! conn "user1" {:name "User 1"}))]

    (testing "add user"
      (let [response (try-add-user! session cong-id new-user-id)]
        (is (ok? response))
        ;; TODO: check the result through the API
        (let [users (congregation/get-users (projections/cached-state) cong-id)]
          (is (contains? (set users) new-user-id)))))

    (testing "invalid user"
      (let [response (try-add-user! session cong-id (UUID. 0 1))]
        (is (bad-request? response))
        (is (= {:errors [["no-such-user" "00000000-0000-0000-0000-000000000001"]]}
               (:body response)))))

    (testing "no access"
      (revoke-access-from-all! cong-id)
      (let [response (try-add-user! session cong-id new-user-id)]
        (is (forbidden? response))))))

(deftest set-user-permissions-test
  (let [session (login! app)
        cong-id (create-congregation! session "Congregation")
        user-id (db/with-db [conn {}]
                  (user/save-user! conn "user1" {:name "User 1"}))]
    (is (ok? (try-add-user! session cong-id user-id)))

    ;; TODO: test changing permissions (after new users no more get full admin access)?

    (testing "remove user"
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/set-user-permissions"))
                         (json-body {:userId (str user-id)
                                     :permissions []})
                         (merge session)
                         app)]
        (is (ok? response))
        ;; TODO: check the result through the API
        (let [users (congregation/get-users (projections/cached-state) cong-id)]
          (is (not (contains? (set users) user-id))))))

    (testing "invalid user"
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/set-user-permissions"))
                         (json-body {:userId (str (UUID. 0 1))
                                     :permissions []})
                         (merge session)
                         app)]
        (is (bad-request? response))
        (is (= {:errors [["no-such-user" "00000000-0000-0000-0000-000000000001"]]}
               (:body response)))))

    (testing "no access"
      (revoke-access-from-all! cong-id)
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/set-user-permissions"))
                         (json-body {:userId (str user-id)
                                     :permissions []})
                         (merge session)
                         app)]
        (is (forbidden? response))))))

(deftest rename-congregation-test
  (let [session (login! app)
        cong-id (create-congregation! session "Old Name")]

    (testing "rename congregation"
      (let [response (try-rename-congregation! session cong-id "New Name")]
        (is (ok? response)))
      (let [response (-> (request :get (str "/api/congregation/" cong-id))
                         (merge session)
                         app)]
        (is (ok? response))
        (is (= "New Name" (:name (:body response))))))

    (testing "no access"
      (revoke-access-from-all! cong-id)
      (let [response (try-rename-congregation! session cong-id "should not be allowed")]
        (is (forbidden? response))))))

(defn- fake-share-key-generator [unique-prefix]
  (let [*counter (atom 0)]
    (fn []
      ;; XXX: to avoid conflicts, each deftest requires its own unique-prefix,
      ;;      because the db-fixture doesn't empty the database between deftests
      (str unique-prefix (swap! *counter inc)))))

(deftest share-territory-link-test
  (let [*session (atom (login! app))
        cong-id (create-congregation! @*session "Congregation")
        territory-id (create-territory! cong-id)
        share-key "share-territory-link-test1"]

    (testing "create a share link"
      (binding [share/generate-share-key (fake-share-key-generator "share-territory-link-test")]
        (let [response (-> (request :post (str "/api/congregation/" cong-id "/territory/" territory-id "/share"))
                           (json-body {})
                           (merge @*session)
                           app)]
          (is (ok? response))
          (is (= {:key share-key
                  :url (str "http://localhost:8080/share/" share-key "/123")}
                 (:body response))))))

    ;; TODO: cannot link to a territory which doesn't belong to the specified congregation

    ;; The rest of this test is ran as an anonymous user
    (reset! *session nil)

    (testing "open share link"
      (let [response (-> (request :get (str "/api/share/" share-key))
                         (merge @*session)
                         app)]
        (reset! *session (get-session response)) ; TODO: stateful HTTP client which remembers the cookies automatically
        (is (ok? response))
        (is (= {:congregation (str cong-id)
                :territory (str territory-id)}
               (:body response)))))

    (testing "records that the share was opened"
      (is (some? (:share/last-opened (share/find-share-by-key (projections/cached-state) share-key)))))

    (testing "can view the shared territory"
      ;; TODO: API to get one territory by ID
      (let [response (-> (request :get (str "/api/congregation/" cong-id))
                         (merge @*session)
                         app)]
        (is (ok? response))
        (is (= (str cong-id) (:id (:body response))))
        (is (contains? (->> (:territories (:body response))
                            (map :id)
                            (set))
                       (str territory-id)))

        (testing "- but cannot view other territories"
          (is (= 1 (count (:territories (:body response))))))

        (testing "- cannot view other congregation details"
          (is (= [] (:regions (:body response)))
              "regions")
          (is (= [] (:congregationBoundaries (:body response)))
              "congregation boundaries")
          (is (= [] (:cardMinimapViewports (:body response)))
              "card minimap viewports")
          (is (= [] (:users (:body response)))
              "users"))

        (testing "- can view do-not-calls"
          (let [response (-> (request :get (str "/api/congregation/" cong-id "/territory/" territory-id))
                             (merge @*session)
                             app)]
            (is (ok? response))
            (is (= {:id (str territory-id)
                    :doNotCalls "the do-not-calls"}
                   (select-keys (:body response) [:id :doNotCalls])))))

        (testing "- but cannot edit do-not-calls"
          (let [response (try-edit-do-not-calls! @*session cong-id territory-id)]
            (is (unauthorized? response))))))

    (testing "non-existing share link"
      (let [response (-> (request :get "/api/share/foo")
                         (merge @*session)
                         app)]
        (is (not-found? response))
        (is (= {:message "Share not found"}
               (:body response)))))))

(deftest share-demo-territory-link-test
  ;; all of this is can be done anonymously, because the demo doesn't require logging in
  (let [cong-id (create-congregation-without-user! "foo")
        territory-id (create-territory! cong-id)
        share-key (share/demo-share-key territory-id)
        state-before (projections/cached-state)]
    (binding [config/env (assoc config/env :demo-congregation cong-id)]

      (testing "create a demo share link"
        (binding [share/generate-share-key (fake-share-key-generator "share-territory-link-test")]
          (let [response (-> (request :post (str "/api/congregation/demo/territory/" territory-id "/share"))
                             (json-body {})
                             app)]
            (is (ok? response))
            (is (= {:key share-key
                    :url (str "http://localhost:8080/share/" share-key "/123")}
                   (:body response))))))

      (testing "open share link"
        (let [response (-> (request :get (str "/api/share/" share-key))
                           app)]
          (is (ok? response))
          (is (= {:congregation "demo"
                  :territory (str territory-id)}
                 (:body response)))))

      (testing "does NOT create a share, nor record that a share was opened"
        (let [state-after (projections/cached-state)]
          (is (= state-before state-after)))))))

(deftest create-qr-code-test
  (let [*session (atom (login! app))
        cong-id (create-congregation! @*session "Congregation")
        territory-id (create-territory! cong-id)
        territory-id2 (create-territory! cong-id)
        territory-id3 (create-territory! cong-id)]

    (testing "error: random number generator creates non-unique share keys"
      (binding [share/generate-share-key (constantly "constantFakeShare")]
        (let [response (-> (request :post (str "/api/congregation/" cong-id "/generate-qr-codes"))
                           (json-body {:territories [territory-id territory-id2]})
                           (merge @*session)
                           app)]
          (is (conflict? response))

          (let [share-keys (:territory-bro.domain.share/share-keys (current-state))]
            (is (not (contains? share-keys "constantFakeShare"))
                "transaction should have been aborted and no shares created")))))

    (binding [share/generate-share-key (fake-share-key-generator "create-qr-code-test")]
      (testing "create QR code links"
        (let [response (-> (request :post (str "/api/congregation/" cong-id "/generate-qr-codes"))
                           (json-body {:territories [territory-id territory-id2]})
                           (merge @*session)
                           app)]
          (is (ok? response))
          (is (= {:qrCodes [{:territory (str territory-id)
                             :key "create-qr-code-test1"
                             :url "https://qr.territorybro.com/create-qr-code-test1"}
                            {:territory (str territory-id2)
                             :key "create-qr-code-test2"
                             :url "https://qr.territorybro.com/create-qr-code-test2"}]}
                 (:body response)))

          (let [share-keys (:territory-bro.domain.share/share-keys (current-state))]
            (is (contains? share-keys "create-qr-code-test1"))
            (is (contains? share-keys "create-qr-code-test2")))))

      (testing "generated QR codes are cached"
        (let [response (-> (request :post (str "/api/congregation/" cong-id "/generate-qr-codes"))
                           (json-body {:territories [territory-id ; the previous test already generated a share for this territory
                                                     territory-id3]})
                           (merge @*session)
                           app)]
          (is (ok? response))
          (is (= {:qrCodes [{:territory (str territory-id)
                             :key "create-qr-code-test1" ; same key as in previous test, instead of #3 here and #4 in the next
                             :url "https://qr.territorybro.com/create-qr-code-test1"}
                            {:territory (str territory-id3)
                             :key "create-qr-code-test3"
                             :url "https://qr.territorybro.com/create-qr-code-test3"}]}
                 (:body response))))))

    (testing "QR code links redirect to the same URL as shared links"
      (let [response (http/get "https://qr.territorybro.com/abc123" {:redirect-strategy :none})]
        (is (temporary-redirect? response))
        (is (= "https://beta.territorybro.com/share/abc123"
               (get-in response [:headers "Location"])))))

    (testing "create QR code links for demo congregation"
      (let [demo-territory1 (UUID/randomUUID)
            demo-territory2 (UUID/randomUUID)
            demo-key1 (share/demo-share-key demo-territory1)
            demo-key2 (share/demo-share-key demo-territory2)
            state-before (current-state)
            ;; does not require logging in
            response (-> (request :post (str "/api/congregation/demo/generate-qr-codes"))
                         (json-body {:territories [demo-territory1 demo-territory2]})
                         app)
            state-after (current-state)]
        (is (ok? response))
        (is (= {:qrCodes [{:territory (str demo-territory1)
                           :key demo-key1
                           :url (str "https://qr.territorybro.com/" demo-key1)}
                          {:territory (str demo-territory2)
                           :key demo-key2
                           :url (str "https://qr.territorybro.com/" demo-key2)}]}
               (:body response)))
        (is (= state-before state-after)
            "does not write anything to application state")))

    ;; TODO: cannot link to a territory which doesn't belong to the specified congregation

    #__))

(deftest gis-changes-sync-test
  (let [session (login! app)
        cong-id (create-congregation! session "Old Name")
        db-spec (get-gis-db-spec session cong-id)
        territory-id (UUID/randomUUID)
        region-id (UUID/randomUUID)
        congregation-boundary-id (UUID/randomUUID)
        card-minimap-viewport-id (UUID/randomUUID)
        conflicting-stream-id (create-congregation-without-user! "foo")]

    (testing "write to GIS database"
      (jdbc/with-db-transaction [conn db-spec]
        (jdbc/execute! conn ["insert into territory (id, number, addresses, subregion, meta, location) values (?, ?, ?, ?, ?::jsonb, ?::public.geography)"
                             territory-id "123" "the addresses" "the region" {:foo "bar"} testdata/wkt-multi-polygon2])
        (jdbc/execute! conn ["update territory set location = ?::public.geography where id = ?"
                             testdata/wkt-multi-polygon territory-id])

        (jdbc/execute! conn ["insert into subregion (id, name, location) values (?, ?, ?::public.geography)"
                             region-id "Somewhere" testdata/wkt-multi-polygon2])
        (jdbc/execute! conn ["update subregion set location = ?::public.geography where id = ?"
                             testdata/wkt-multi-polygon region-id])

        (jdbc/execute! conn ["insert into congregation_boundary (id, location) values (?, ?::public.geography)"
                             congregation-boundary-id testdata/wkt-multi-polygon2])
        (jdbc/execute! conn ["update congregation_boundary set location = ?::public.geography where id = ?"
                             testdata/wkt-multi-polygon congregation-boundary-id])

        (jdbc/execute! conn ["insert into card_minimap_viewport (id, location) values (?, ?::public.geography)"
                             card-minimap-viewport-id testdata/wkt-polygon2])
        (jdbc/execute! conn ["update card_minimap_viewport set location = ?::public.geography where id = ?"
                             testdata/wkt-polygon card-minimap-viewport-id]))
      (sync-gis-changes!))

    (testing "changes to GIS database are synced to event store"
      (let [state (projections/cached-state)]
        (is (= {:territory/id territory-id
                :territory/number "123"
                :territory/addresses "the addresses"
                :territory/region "the region"
                :territory/meta {:foo "bar"}
                :territory/location testdata/wkt-multi-polygon}
               (get-in state [::territory/territories cong-id territory-id])))
        (is (= {:region/id region-id
                :region/name "Somewhere"
                :region/location testdata/wkt-multi-polygon}
               (get-in state [::region/regions cong-id region-id])))
        (is (= {:congregation-boundary/id congregation-boundary-id
                :congregation-boundary/location testdata/wkt-multi-polygon}
               (get-in state [::congregation-boundary/congregation-boundaries cong-id congregation-boundary-id])))
        (is (= {:card-minimap-viewport/id card-minimap-viewport-id
                :card-minimap-viewport/location testdata/wkt-polygon}
               (get-in state [::card-minimap-viewport/card-minimap-viewports cong-id card-minimap-viewport-id])))))

    (testing "syncing changes is idempotent"
      (let [state-before (projections/cached-state)]
        (sync-gis-changes!) ;; should not process the already processed changes
        (is (identical? state-before (projections/cached-state)))))

    (testing "syncing changes is triggered automatically"
      (let [new-id (jdbc/with-db-transaction [conn db-spec]
                     (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon))
            deadline (-> (Instant/now) (.plus (Duration/ofSeconds 5)))]
        (loop []
          (cond
            (= new-id (get-in (projections/cached-state) [::congregation-boundary/congregation-boundaries cong-id new-id :congregation-boundary/id]))
            (is true "was synced")

            (-> (Instant/now) (.isAfter deadline))
            (is false "deadline reached, was not synced")

            :else
            (do (Thread/sleep 10)
                (recur))))))

    (testing "changing the ID will delete the old territory and create a new one"
      (let [new-territory-id (UUID/randomUUID)]
        (jdbc/with-db-transaction [conn db-spec]
          (jdbc/execute! conn ["update territory set id = ? where id = ?"
                               new-territory-id territory-id]))
        (sync-gis-changes!)
        (let [state (projections/cached-state)]
          (is (nil? (get-in state [::territory/territories cong-id territory-id]))
              "old ID")
          (is (= {:territory/id new-territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-multi-polygon}
                 (get-in state [::territory/territories cong-id new-territory-id]))
              "new ID"))))

    (testing "stream ID conflict on insert -> generates a new replacement ID"
      (jdbc/with-db-transaction [conn db-spec]
        (jdbc/execute! conn ["delete from subregion"])
        (jdbc/execute! conn ["insert into subregion (id, name, location) values (?, ?, ?::public.geography)"
                             conflicting-stream-id "Conflicting insert" testdata/wkt-multi-polygon]))
      (sync-gis-changes!)
      (let [state (projections/cached-state)
            replacement-id (first (keys (get-in state [::region/regions cong-id])))]
        (is (some? replacement-id))
        (is (not= conflicting-stream-id replacement-id))
        (is (= [{:region/id replacement-id
                 :region/name "Conflicting insert"
                 :region/location testdata/wkt-multi-polygon}]
               (vals (get-in state [::region/regions cong-id]))))))

    (testing "stream ID conflict on ID update -> generates a new replacement ID"
      ;; combination of the previous two tests, to ensure that the features to not conflict
      (jdbc/with-db-transaction [conn db-spec]
        (jdbc/execute! conn ["update subregion set id = ?, name = ?"
                             conflicting-stream-id "Conflicting update"]))
      (sync-gis-changes!)
      (let [state (projections/cached-state)
            replacement-id (first (keys (get-in state [::region/regions cong-id])))]
        (is (some? replacement-id))
        (is (not= conflicting-stream-id replacement-id))
        (is (= [{:region/id replacement-id
                 :region/name "Conflicting update"
                 :region/location testdata/wkt-multi-polygon}]
               (vals (get-in state [::region/regions cong-id]))))))

    (testing "stream ID conflict on insert, delete, insert with same ID"
      (let [congregation-boundary-id (jdbc/with-db-transaction [conn db-spec]
                                       (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon))]
        (jdbc/with-db-transaction [conn db-spec]
          (jdbc/execute! conn ["delete from congregation_boundary"]))
        (sync-gis-changes!)

        (jdbc/with-db-transaction [conn db-spec]
          (jdbc/execute! conn ["insert into congregation_boundary (id, location) values (?, ?::public.geography)"
                               congregation-boundary-id testdata/wkt-multi-polygon2]))
        (sync-gis-changes!) ; this used to crash with an "event stream already exists" error

        (let [state (projections/cached-state)]
          (is (= {:congregation-boundary/id congregation-boundary-id
                  :congregation-boundary/location testdata/wkt-multi-polygon2}
                 (get-in state [::congregation-boundary/congregation-boundaries cong-id congregation-boundary-id]))))))))
