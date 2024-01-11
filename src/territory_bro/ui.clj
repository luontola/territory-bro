;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui
  (:require [clojure.string :as str]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.http-response :refer :all]
            [ring.util.response :as response]
            [territory-bro.ui.territory-page :as territory-page]))

(defn html-response [html]
  (when (some? html)
    (-> (ok (str html))
        (response/content-type "text/html"))))

(defroutes ui-routes
  (GET "/" [] (ok "Territory Bro"))

  ;; TODO: switch to reitit and remove the duplication of setting *page-path* using a middleware
  (GET "/congregation/:congregation/territories/:territory" request
    (binding [territory-page/*page-path* (:uri request)]
      (html-response (territory-page/page request))))

  (POST "/congregation/:congregation/territories/:territory/do-not-calls/edit" request
    (binding [territory-page/*page-path* (-> (:uri request)
                                             (str/replace #"/do-not-calls/edit$" ""))]
      (html-response (territory-page/do-not-calls--edit request))))

  (POST "/congregation/:congregation/territories/:territory/do-not-calls/save" request
    (binding [territory-page/*page-path* (-> (:uri request)
                                             (str/replace #"/do-not-calls/save$" ""))]
      (html-response (territory-page/do-not-calls--save request))))

  (POST "/congregation/:congregation/territories/:territory/share-link/open" request
    (binding [territory-page/*page-path* (-> (:uri request)
                                             (str/replace #"/share-link/open$" ""))]
      (html-response (territory-page/share-link--open request))))

  (POST "/congregation/:congregation/territories/:territory/share-link/close" request
    (binding [territory-page/*page-path* (-> (:uri request)
                                             (str/replace #"/share-link/close$" ""))]
      (html-response (territory-page/share-link--close request)))))
