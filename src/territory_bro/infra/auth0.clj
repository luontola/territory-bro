;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.auth0
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [mount.core :as mount]
            [ring.util.response :as response]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.util :refer [getx]])
  (:import (com.auth0 AuthenticationController)
           (com.auth0.jwk JwkProviderBuilder)
           (javax.servlet.http HttpServletRequest HttpServletResponse)))

(mount/defstate auth-controller
  :start
  (let [domain (getx config/env :auth0-domain)
        client-id (getx config/env :auth0-client-id)
        client-secret (getx config/env :auth0-client-secret)
        jwk-provider (-> (JwkProviderBuilder. ^String domain)
                         (.build))]
    (-> (AuthenticationController/newBuilder domain client-id client-secret)
        (.withJwkProvider jwk-provider)
        (.build))))

(defn servlet-adapter [ring-request]
  (let [request (reify HttpServletRequest)
        response (reify HttpServletResponse)]
    [request response]))

(defn login-handler [request]
  (pp/pprint config/env)
  (let [public-url (-> (getx config/env :public-url)
                       (str/replace "8080" "8081")) ; TODO: remove me
        callback-url (str public-url "/login-callback")
        [request response] (servlet-adapter request)
        authorize-url (-> (.buildAuthorizeUrl auth-controller request response callback-url)
                          (.build))]
    (prn 'xxx request response authorize-url)
    (response/redirect authorize-url)))

;; TODO: adapter for Ring request -> HttpServletRequest
;; TODO: adapter for HttpServletResponse -> Ring response
;; TODO: adapter for Ring session -> HttpSession -> Ring session
;; TODO: adapter for com.auth0.AuthenticationController
