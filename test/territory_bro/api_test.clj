;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.api-test
  (:require [clj-xpath.core :as xpath]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [ring.util.http-predicates :refer :all]
            [territory-bro.api :as api]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.region :as region]
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
            [territory-bro.test.fixtures :refer [db-fixture api-fixture]])
  (:import (java.time Instant Duration)
           (java.util UUID)
           (territory_bro ValidationException NoPermitException WriteConflictException)))

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


;;;; API Helpers

(defn login! [app]
  (let [response (-> (request :post "/api/login")
                     (json-body {:idToken jwt-test/token})
                     app
                     (assert-response ok?))]
    {:cookies (get-cookies response)}))

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
  (-> (request :post (str "/api/congregation/" cong-id "/rename"))
      (json-body {:name name})
      (merge session)
      app))

(defn try-add-user! [session cong-id user-id]
  (-> (request :post (str "/api/congregation/" cong-id "/add-user"))
      (json-body {:userId (str user-id)})
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

(deftest api-command-test
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
                                                   args)))]
          (is (= {:body {:message "OK"}
                  :status 200
                  :headers {}}
                 (api/api-command! conn state command)))))

      (testing "failed command: validation"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (ValidationException. [[:dummy-error 123]])))]
          (is (= {:body {:errors [[:dummy-error 123]]}
                  :status 400
                  :headers {}}
                 (api/api-command! conn state command)))))

      (testing "failed command: permission check"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (NoPermitException. test-user [:dummy-permission 123])))]
          (is (= {:body {:message "Forbidden"}
                  :status 403
                  :headers {}}
                 (api/api-command! conn state command)))))

      (testing "failed command: write conflict"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (WriteConflictException. "dummy error")))]
          (is (= {:body {:message "Conflict"}
                  :status 409
                  :headers {}}
                 (api/api-command! conn state command)))))

      (testing "failed command: internal error"
        (with-redefs [dispatcher/command! (fn [& _]
                                            (throw (RuntimeException. "dummy error")))]
          (is (= {:body {:message "Internal Server Error"}
                  :status 500
                  :headers {}}
                 (api/api-command! conn state command))))))))

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
    (let [response (-> (request :get "/api/congregations")
                       app)]
      (is (unauthorized? response))))

  (let [session (login! app)]
    (testing "after login"
      (let [response (-> (request :get "/api/congregations")
                         (merge session)
                         app)]
        (is (ok? response))))

    (testing "after logout"
      (logout! app session)
      (let [response (-> (request :get "/api/congregations")
                         (merge session)
                         app)]
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
          (is (= "http://localhost/" (get-in response [:headers "Location"]))))))

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
        response (-> (request :get "/api/congregations")
                     (merge session)
                     app)]
    (is (ok? response))
    (is (sequential? (:body response))))

  (testing "requires login"
    (let [response (-> (request :get "/api/congregations")
                       app)]
      (is (unauthorized? response)))))

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
                  :permissions {:viewCongregation true}}
                 (select-keys (:body response) [:id :name :users :permissions]))
              "returns an anonymized read-only congregation")))

      (testing "requires login"
        (let [response (-> (request :get (str "/api/congregation/demo"))
                           app)]
          (is (unauthorized? response)))))

    (binding [config/env (assoc config/env :demo-congregation nil)]
      (testing "no demo congregation"
        (let [response (-> (request :get (str "/api/congregation/demo"))
                           (merge session)
                           app)]
          (is (forbidden? response))
          (is (= "No demo congregation" (:body response))))))))

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

(deftest gis-changes-sync-test
  (let [session (login! app)
        cong-id (create-congregation! session "Old Name")
        db-spec (get-gis-db-spec session cong-id)
        territory-id (UUID/randomUUID)
        subregion-id (UUID/randomUUID)
        congregation-boundary-id (UUID/randomUUID)
        card-minimap-viewport-id (UUID/randomUUID)]

    (testing "write to GIS database"
      (jdbc/with-db-transaction [conn db-spec]
        (jdbc/execute! conn ["insert into territory (id, number, addresses, subregion, meta, location) values (?, ?, ?, ?, ?::jsonb, ?::public.geography)"
                             territory-id "123" "the addresses" "the subregion" {:foo "bar"} testdata/wkt-multi-polygon2])
        (jdbc/execute! conn ["update territory set location = ?::public.geography where id = ?"
                             testdata/wkt-multi-polygon territory-id])

        (jdbc/execute! conn ["insert into subregion (id, name, location) values (?, ?, ?::public.geography)"
                             subregion-id "Somewhere" testdata/wkt-multi-polygon2])
        (jdbc/execute! conn ["update subregion set location = ?::public.geography where id = ?"
                             testdata/wkt-multi-polygon subregion-id])

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
                :territory/subregion "the subregion"
                :territory/meta {:foo "bar"}
                :territory/location testdata/wkt-multi-polygon}
               (get-in state [::territory/territories cong-id territory-id])))
        (is (= {:subregion/id subregion-id
                :subregion/name "Somewhere"
                :subregion/location testdata/wkt-multi-polygon}
               (get-in state [::region/subregions cong-id subregion-id])))
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

    (testing "on stream ID conflict a new replacement ID will be generated"
      (let [conflicting-stream-id (create-congregation-without-user! "foo")]
        (jdbc/with-db-transaction [conn db-spec]
          (jdbc/execute! conn ["insert into subregion (id, name, location) values (?, ?, ?::public.geography)"
                               conflicting-stream-id "Conflicting ID" testdata/wkt-multi-polygon]))
        (sync-gis-changes!)
        (let [state (projections/cached-state)
              replacement-id (-> (set (keys (get-in state [::region/subregions cong-id])))
                                 (disj subregion-id) ; produced by earlier tests
                                 (first))]
          (is (some? replacement-id))
          (is (not= conflicting-stream-id replacement-id))
          (is (= [{:subregion/id replacement-id
                   :subregion/name "Conflicting ID"
                   :subregion/location testdata/wkt-multi-polygon}
                  {:subregion/id subregion-id
                   :subregion/name "Somewhere"
                   :subregion/location testdata/wkt-multi-polygon}]
                 (->> (vals (get-in state [::region/subregions cong-id]))
                      (sort-by :subregion/name)))))))))

;; TODO: delete territory and then restore it to same congregation
