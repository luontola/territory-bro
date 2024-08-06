;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.auth0
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [meta-merge.core :refer [meta-merge]]
            [mount.core :as mount]
            [ring.util.codec :as codec]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.util :as util]
            [territory-bro.infra.util :refer [getx]])
  (:import (com.auth0 AuthenticationController IdentityVerificationException)
           (com.auth0.client.auth AuthAPI)
           (com.auth0.jwk JwkProviderBuilder)
           (com.auth0.jwt JWT)
           (java.net URL)
           (javax.servlet.http Cookie HttpServletRequest HttpServletResponse HttpSession)))

(mount/defstate ^:dynamic ^AuthenticationController auth-controller
  :start
  (let [domain (getx config/env :auth0-domain)
        client-id (getx config/env :auth0-client-id)
        client-secret (getx config/env :auth0-client-secret)
        jwk-provider (-> (JwkProviderBuilder. ^String domain)
                         (.build))]
    (-> (AuthenticationController/newBuilder domain client-id client-secret)
        (.withJwkProvider jwk-provider)
        (.build))))

(defn- request-url [request]
  (let [base-url (URL. (:public-url config/env))]
    (URL. base-url (:uri request)))
  #_(let [url (URL. (name (:scheme request))
                    (:server-name request)
                    (:server-port request)
                    (:uri request))]
      (if (= (.getDefaultPort url) (.getPort url))
        (request-url (assoc request :server-port -1))
        url)))

(defn ring->servlet [request]
  (let [*response (atom (-> (response/response "")
                            (merge (select-keys request [:session]))))
        servlet-session (reify HttpSession
                          (getAttribute [_ name]
                            (get-in @*response [:session ::servlet name]))
                          (setAttribute [_ name value]
                            (swap! *response assoc-in [:session ::servlet name] value))
                          (removeAttribute [_ name]
                            (swap! *response update-in [:session ::servlet] dissoc name)))
        servlet-request (reify HttpServletRequest
                          (getRequestURL [_]
                            (StringBuffer. (str (request-url request))))
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

(defn public-url []
  (getx config/env :public-url))

(defn return-to-url [{:keys [uri query-string]}]
  (if (some? query-string)
    (str uri "?" query-string)
    (str uri)))

(defn login-url [request]
  (str "/login?return-to-url=" (codec/url-encode (return-to-url request))))

(defn wrap-redirect-to-login [handler]
  (fn [request]
    (let [response (handler request)]
      (if (= 401 (:status response))
        (http-response/see-other (login-url request))
        response))))

(defn login-handler [ring-request]
  (let [{:keys [return-to-url]} (:params ring-request)
        callback-url (str (public-url)
                          "/login-callback"
                          (when (some? return-to-url)
                            ;; Double URL-encoding is needed, or "/path?foo=bar&gazonk"
                            ;; would be truncated to "/path?foo=bar" after returning from Auth0.
                            ;; Likely this is because com.auth0.client.auth.AuthorizeUrlBuilder constructor
                            ;; uses addEncodedQueryParameter instead of addQueryParameter for redirect_uri.
                            ;; However, if we try to URL-encode the whole URL instead of just the query string,
                            ;; AuthorizeUrl wouldn't accept it as a valid URL.
                            (codec/url-encode (str "?return-to-url=" (codec/url-encode return-to-url)))))
        [servlet-request servlet-response *ring-response] (ring->servlet ring-request)
        authorize-url (-> (.buildAuthorizeUrl auth-controller servlet-request servlet-response callback-url)
                          (.withScope "openid email profile")
                          (.withParameter "prompt" "select_account")
                          (.build))]
    (meta-merge @*ring-response
                (http-response/see-other authorize-url))))

(defn login-callback-handler [ring-request]
  (try
    (let [{:keys [return-to-url]} (:params ring-request)
          [servlet-request servlet-response *ring-response] (ring->servlet ring-request)
          tokens (.handle auth-controller servlet-request servlet-response)
          id-token (-> (.getIdToken tokens)
                       (JWT/decode)
                       (.getPayload)
                       (util/decode-base64url)
                       (json/read-value))
          user-id (dmz/save-user-from-jwt! id-token)
          session (-> (:session @*ring-response)
                      (assoc ::tokens tokens)
                      (merge (auth/user-session id-token user-id)))]
      (log/info "Logged in using OIDC. ID token was" (pr-str id-token))
      (-> (http-response/see-other (or return-to-url "/"))
          (assoc :session session)))
    (catch IdentityVerificationException e
      (log/warn e "Login failed")
      (http-response/forbidden "Login failed"))))

(defn- build-fake-id-token [params]
  (let [id-token (select-keys params auth/user-profile-keys)
        required-params #{:sub :name :email}
        missing-params (set/difference required-params (set (keys id-token)))]
    (when-not (empty? missing-params)
      (http-response/bad-request! (str "Missing parameters: "
                                       (->> missing-params
                                            (sort)
                                            (map name)
                                            (str/join ", ")))))
    id-token))

(defn dev-login-handler [request]
  (if (getx config/env :dev)
    (let [id-token (build-fake-id-token (:params request))
          user-id (dmz/save-user-from-jwt! id-token)
          session (-> (:session request)
                      (merge (auth/user-session id-token user-id)))]
      (log/info "Logged in using dev login. ID token was" (pr-str id-token))
      (-> (http-response/see-other "/")
          (assoc :session session)))
    (http-response/forbidden "Dev mode disabled")))

(defn logout-handler [_request]
  (let [domain (getx config/env :auth0-domain)
        client-id (getx config/env :auth0-client-id)
        client-secret (getx config/env :auth0-client-secret)
        api (AuthAPI. domain client-id client-secret)
        return-to-url (str (public-url) "/")
        logout-url (-> (.logoutUrl api return-to-url true)
                       (.build))]
    (-> (http-response/see-other logout-url)
        (assoc :session nil))))

(def routes
  [["/login"
    {:get {:handler login-handler}}]
   ["/login-callback"
    {:get {:middleware [dmz/wrap-db-connection]
           :handler login-callback-handler}}]
   ["/dev-login"
    {:get {:middleware [dmz/wrap-db-connection]
           :handler dev-login-handler}}]
   ["/logout"
    {:get {:handler logout-handler}}]])
