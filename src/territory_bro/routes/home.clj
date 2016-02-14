(ns territory-bro.routes.home
  (:require [territory-bro.db.core :as db]
            [territory-bro.domain :as domain]
            [territory-bro.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :refer [redirect]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import (java.time LocalDate)))

(defn home-page []
  (layout/render "home.html"
                 {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn territory-cards-page []
  (layout/render "territory-cards.html"
                 {:territories (db/find-territories)
                  :today       (LocalDate/now)}))

(defn import-territories! [request]
  (let [tempfile (-> request :params :territories :tempfile)]
    (try
      (let [territories (-> tempfile
                            slurp
                            json/read-str
                            domain/geojson-to-territories)]
        (db/transactional
          (db/delete-all-territories!)
          (dorun (map db/create-territory! territories))))
      (finally
        (io/delete-file tempfile))))
  (redirect "/territory-cards"))

(defroutes home-routes
           (GET "/" [] (home-page))
           (GET "/territory-cards" [] (territory-cards-page))
           (POST "/import-territories" request (import-territories! request)))
