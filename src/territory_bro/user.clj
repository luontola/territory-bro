;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.user
  (:require [territory-bro.db :as db])
  (:import (java.util UUID)))

(def ^:private query! (db/compile-queries "db/hugsql/user.sql"))

(defn- format-user [user]
  {::id (:id user)
   ::subject (:subject user)
   ::attributes (:attributes user)})

(defn get-users
  ([conn]
   (get-users conn {}))
  ([conn search]
   (->> (query! conn :get-users search)
        (map format-user)
        (doall))))

(defn get-by-id [conn id]
  (first (get-users conn {:ids [id]})))

(defn get-by-subject [conn subject]
  (first (get-users conn {:subjects [subject]})))

(defn save-user! [conn subject attributes]
  (:id (first (query! conn :save-user {:id (UUID/randomUUID)
                                       :subject subject
                                       :attributes attributes}))))
