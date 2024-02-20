;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.auth0
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [meta-merge.core :refer [meta-merge]]
            [mount.core :as mount]
            [ring.util.response :as response]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.util :refer [getx]])
  (:import (com.auth0 AuthenticationController)
           (com.auth0.jwk JwkProviderBuilder)
           (javax.servlet.http Cookie HttpServletRequest HttpServletResponse HttpSession)))

(mount/defstate ^AuthenticationController auth-controller
  :start
  (let [domain (getx config/env :auth0-domain)
        client-id (getx config/env :auth0-client-id)
        client-secret (getx config/env :auth0-client-secret)
        jwk-provider (-> (JwkProviderBuilder. ^String domain)
                         (.build))]
    (-> (AuthenticationController/newBuilder domain client-id client-secret)
        (.withJwkProvider jwk-provider)
        (.build))))

(defn ring->servlet [request]
  (let [*response (atom (-> (response/response "")
                            (merge (select-keys request [:session]))))
        servlet-session (reify HttpSession
                          ;; TODO: removeAttribute
                          (getAttribute [_ name]
                            (get-in @*response [:session ::servlet name]))
                          (setAttribute [_ name value]
                            (swap! *response assoc-in [:session ::servlet name] value)))
        servlet-request (reify HttpServletRequest
                          ;; TODO: getRequestURL
                          (getSession [_ create]
                            (when (and create (not (:session @*response)))
                              (swap! *response assoc :session {}))
                            (when (:session @*response)
                              servlet-session))
                          (getParameter [_ name]
                            (get-in request [:params (keyword name)]))
                          (getCookies [_]
                            (when-not (empty? (:cookies request))
                              (->> (:cookies request)
                                   (map (fn [[k {v :value}]]
                                          (Cookie. k v)))
                                   (into-array Cookie)))))
        servlet-response (reify HttpServletResponse
                           (addCookie [_ cookie]
                             (swap! *response response/set-cookie (.getName cookie) (.getValue cookie)
                                    (merge (when-some [path (.getPath cookie)]
                                             {:path path})
                                           (when-some [domain (.getDomain cookie)]
                                             {:domain domain})
                                           (let [max-age (.getMaxAge cookie)]
                                             (when (<= 0 max-age)
                                               {:max-age max-age}))
                                           (when-let [secure (.getSecure cookie)]
                                             {:secure secure})
                                           (when-let [http-only (.isHttpOnly cookie)]
                                             {:http-only http-only}))))
                           (getHeader [_ name]
                             (first (response/get-header @*response name)))
                           (getHeaders [_ name]
                             (sequence (response/get-header @*response name)))
                           (addHeader [_ name value]
                             (swap! *response response/update-header name concat (list value))))]
    [servlet-request servlet-response *response]))

(defn login-handler [ring-request]
  (let [public-url (-> (getx config/env :public-url)
                       (str/replace "8080" "8081")) ; TODO: remove me
        callback-url (str public-url "/login-callback")
        [servlet-request servlet-response *ring-response] (ring->servlet ring-request)
        authorize-url (-> (.buildAuthorizeUrl auth-controller servlet-request servlet-response callback-url)
                          (.build))]
    (meta-merge @*ring-response
                (response/redirect authorize-url :see-other))))

(defn login-callback-handler [ring-request]
  (prn '----------)
  (pp/pprint ring-request)
  ;; TODO
  (response/response "TODO"))

(defn logout-handler [ring-request]
  ;; TODO
  (response/response "TODO"))

;; TODO: adapter for Ring request -> HttpServletRequest
;; TODO: adapter for HttpServletResponse -> Ring response
;; TODO: adapter for Ring session -> HttpSession -> Ring session
;; TODO: adapter for com.auth0.AuthenticationController
