;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.auth0-test
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [territory-bro.infra.auth0 :as auth0]
            [territory-bro.infra.config :as config]))

(defn config-fixture [f]
  (mount/start #'config/env
               #'auth0/auth-controller)
  (f)
  (mount/stop))

(use-fixtures :once config-fixture)

(deftest login-handler-test
  (let [request {:request-method :get
                 :uri "/login"
                 :query-string nil
                 :headers {}
                 :session {}}]
    (is (auth0/login-handler request))))
