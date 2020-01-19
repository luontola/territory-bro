;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:slow territory-bro.api-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [ring.util.http-predicates :refer :all]
            [territory-bro.api :as api]
            [territory-bro.authentication :as auth]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.fixtures :refer [db-fixture api-fixture]]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.json :as json]
            [territory-bro.jwt :as jwt]
            [territory-bro.jwt-test :as jwt-test]
            [territory-bro.projections :as projections]
            [territory-bro.router :as router]
            [territory-bro.user :as user])
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
    (string? body) body
    :else (json/parse-string (slurp (io/reader body)))))

(defn read-body [response]
  (update response :body parse-json))

(defn app [request]
  (-> request router/app read-body))

(defn assert-response [response predicate]
  (assert (predicate response)
          (str "Unexpected response " response))
  response)

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


(deftest format-for-api-test
  (is (= {} (api/format-for-api {})))
  (is (= {"foo" 1} (api/format-for-api {:foo 1})))
  (is (= {"fooBar" 1} (api/format-for-api {:foo-bar 1})))
  (is (= {"bar" 1} (api/format-for-api {:foo/bar 1})))
  (is (= [{"foo" 1} {"bar" 2}] (api/format-for-api [{:foo 1} {:bar 2}]))))

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

(defn- get-user-id [session]
  (let [response (-> (request :get "/api/settings")
                     (merge session)
                     app)]
    (assert (ok? response) {:response response})
    (UUID/fromString (get-in (json/parse-string (:body response)) [:user :id]))))

(defn- create-congregation! [name]
  (db/with-db [conn {}]
    (let [cong-id (UUID/randomUUID)]
      (dispatcher/command! conn (projections/cached-state)
                           {:command/type :congregation.command/create-congregation
                            :command/time (Instant/now)
                            :command/system "test"
                            :congregation/id cong-id
                            :congregation/name name})
      cong-id)))

(deftest super-user-test
  (let [cong-id (create-congregation! "sudo test")
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
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/add-user"))
                         (json-body {:userId (str user-id)})
                         (merge session)
                         app)]
        (is (ok? response))))))

(deftest create-congregation-test
  (let [session (login! app)
        user-id (get-user-id session)
        response (-> (request :post "/api/congregations")
                     (json-body {:name "foo"})
                     (merge session)
                     app)]
    (is (ok? response))
    (is (:id (:body response)))

    (let [cong-id (UUID/fromString (:id (:body response)))]
      (testing "grants access to the current user"
        (is (= 1 (count (congregation/get-users (projections/cached-state) cong-id)))))

      (testing "creates a GIS user for the current user"
        (let [gis-user (gis-user/get-gis-user (projections/cached-state) cong-id user-id)]
          (is gis-user)
          (is (true? (db/with-db [conn {:read-only? true}]
                       (gis-db/user-exists? conn (:gis-user/username gis-user)))))))))

  (testing "requires login"
    (let [response (-> (request :post "/api/congregations")
                       (json-body {:name "foo"})
                       app)]
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

(defn revoke-access-from-all! [cong-id]
  (db/with-db [conn {}]
    (let [state (projections/cached-state)]
      (doseq [user-id (congregation/get-users state cong-id)]
        (dispatcher/command! conn state
                             {:command/type :congregation.command/set-user-permissions
                              :command/time (Instant/now)
                              :command/system "test"
                              :congregation/id cong-id
                              :user/id user-id
                              :permission/ids []}))))
  (projections/refresh-async!)
  (projections/await-refreshed (Duration/ofSeconds 1)))


(deftest get-congregation-test
  (let [session (login! app)
        response (-> (request :post "/api/congregations")
                     (json-body {:name "foo"})
                     (merge session)
                     app
                     (assert-response ok?))
        cong-id (UUID/fromString (:id (:body response)))]

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
        response (-> (request :post "/api/congregations")
                     (json-body {:name "foo"})
                     (merge session)
                     app
                     (assert-response ok?))
        cong-id (UUID/fromString (:id (:body response)))]
    (binding [config/env (assoc config/env :demo-congregation cong-id)]

      (testing "get demo congregation"
        (let [response (-> (request :get (str "/api/congregation/demo"))
                           (merge session)
                           app)]
          (is (ok? response))
          (is (= "demo" (:id (:body response)))
              "replaces original ID")
          (is (= "Demo Congregation" (:name (:body response)))
              "replaces original name")
          (is (= [] (:users (:body response)))
              "may not view users")
          (is (= {:viewCongregation true}
                 (:permissions (:body response)))
              "has read-only permissions")))

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
        response (-> (request :post "/api/congregations")
                     (json-body {:name "Example Congregation"})
                     (merge session)
                     app
                     (assert-response ok?))
        cong-id (UUID/fromString (:id (:body response)))]

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
      (projections/refresh-async!)
      (projections/await-refreshed (Duration/ofSeconds 1))

      (let [response (-> (request :get (str "/api/congregation/" cong-id "/qgis-project"))
                         (merge session)
                         app)]
        (is (forbidden? response))
        (is (str/includes? (:body response) "No GIS access"))))))

(deftest add-user-test
  (let [session (login! app)
        response (-> (request :post "/api/congregations")
                     (json-body {:name "Congregation"})
                     (merge session)
                     app
                     (assert-response ok?))
        cong-id (UUID/fromString (:id (:body response)))
        new-user-id (db/with-db [conn {}]
                      (user/save-user! conn "user1" {:name "User 1"}))]

    (testing "add user"
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/add-user"))
                         (json-body {:userId (str new-user-id)})
                         (merge session)
                         app)]
        (is (ok? response)))
      ;; TODO: check the result through the API
      (let [users (congregation/get-users (projections/cached-state) cong-id)]
        (is (contains? (set users) new-user-id))))

    (testing "invalid user"
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/add-user"))
                         (json-body {:userId (str (UUID. 0 1))})
                         (merge session)
                         app)]
        (is (bad-request? response))
        (is (= {:errors [["no-such-user" "00000000-0000-0000-0000-000000000001"]]}
               (:body response)))))

    (testing "no access"
      (revoke-access-from-all! cong-id)
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/add-user"))
                         (json-body {:userId (str new-user-id)})
                         (merge session)
                         app)]
        (is (forbidden? response))))))

(deftest set-user-permissions-test
  (let [session (login! app)
        response (-> (request :post "/api/congregations")
                     (json-body {:name "Congregation"})
                     (merge session)
                     app
                     (assert-response ok?))
        cong-id (UUID/fromString (:id (:body response)))
        user-id (db/with-db [conn {}]
                  (user/save-user! conn "user1" {:name "User 1"}))]
    (let [response (-> (request :post (str "/api/congregation/" cong-id "/add-user"))
                       (json-body {:userId (str user-id)})
                       (merge session)
                       app)]
      (is (ok? response)))

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
        response (-> (request :post "/api/congregations")
                     (json-body {:name "Old Name"})
                     (merge session)
                     app
                     (assert-response ok?))
        cong-id (UUID/fromString (:id (:body response)))]

    (testing "rename congregation"
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/rename"))
                         (json-body {:name "New Name"})
                         (merge session)
                         app)]
        (is (ok? response)))
      (let [response (-> (request :get (str "/api/congregation/" cong-id))
                         (merge session)
                         app)]
        (is (ok? response))
        (is (= "New Name" (:name (:body response))))))

    (testing "no access"
      (revoke-access-from-all! cong-id)
      (let [response (-> (request :post (str "/api/congregation/" cong-id "/rename"))
                         (json-body {:name "should not be allowed"})
                         (merge session)
                         app)]
        (is (forbidden? response))))))
