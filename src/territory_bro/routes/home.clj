(ns territory-bro.routes.home
  (:require [territory-bro.db.core :as db]
            [territory-bro.domain :as domain]
            [territory-bro.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :refer [redirect]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))

(defn home-page []
  (layout/render
    "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page []
  (layout/render "about.html"))

(defn territories-page []
  (layout/render "territories.html"))

(defn save-territories! [request]
  (let [tempfile (-> request :params :territories :tempfile)]
    (try
      (doseq [territory (-> tempfile
                            slurp
                            json/read-str
                            domain/geojson-to-territories)]
        (db/create-territory! territory))
      (finally
        (io/delete-file tempfile))))
  (redirect "/territories"))

(defroutes home-routes
           (GET "/" [] (home-page))
           (GET "/about" [] (about-page))
           (GET "/territories" [] (territories-page))
           (POST "/territories" request (save-territories! request)))
