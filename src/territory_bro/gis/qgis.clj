(ns territory-bro.gis.qgis
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def project-template (io/resource "template-territories.qgs"))

(defn generate-project [{:keys [database-host database-name database-schema database-username database-password]}]
  (assert database-host "host is missing")
  (assert database-name "db name is missing")
  (assert database-schema "schema is missing")
  (assert database-username "username is missing")
  (assert database-password "password is missing")
  (-> (slurp project-template)
      (str/replace "HOST_GOES_HERE" database-host)
      (str/replace "DBNAME_GOES_HERE" database-name)
      (str/replace "SCHEMA_GOES_HERE" database-schema)
      (str/replace "USERNAME_GOES_HERE" database-username)
      (str/replace "PASSWORD_GOES_HERE" database-password)))

(defn project-file-name [name]
  (let [name (-> name
                 (str/replace #"[<>:\"/\\|?*]" "") ; not allowed in Windows file names
                 (str/replace #"\s+" " "))
        name (if (str/blank? name)
               "territories"
               name)]
    (str name ".qgs")))
